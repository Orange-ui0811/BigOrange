"""Read-only report input capture and isolated provider usage probes. Never publishes reports."""
import argparse
import concurrent.futures
import hashlib
import json
import re
import subprocess
import sys
import time
import urllib.request
import urllib.error
from pathlib import Path

from export_rag_model_corpus import query

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/rag-b/report-token-probe-20261009'
STAGE = ROOT / '_experiment_staging/report-token-probe-20261009'

def dump(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')

def compact(v):
    return json.dumps(v, ensure_ascii=False, separators=(',', ':'))

def prompts(kind):
    name = 'Beginner' if kind == 'beginner' else 'Career'
    text = (ROOT / f'orange-server/src/main/java/com/orangejd/server/service/{name}ProjectService.java').read_text(encoding='utf-8')
    fragment = text.split('response = aiClient.call(config,', 1)[1].split('0.2,', 1)[0]
    system, user = fragment.split(',\n', 1)
    def strings(s):
        return ''.join(json.loads(x) for x in re.findall(r'"(?:\\.|[^"\\])*"', s))
    return strings(system), strings(user), hashlib.sha256(text.encode()).hexdigest()

def capture():
    # Only published repository metadata and report inputs. No account data.
    projects = query("""SELECT JSON_OBJECT('id',id,'fullName',full_name,'description',COALESCE(description_original,''),
      'language',COALESCE(primary_language,''),'techStack',COALESCE(tech_stack,''),'score',beginner_score,
      'license',COALESCE(license_spdx,''),'quickStart',has_quick_start,'setupSteps',COALESCE(setup_steps,0),
      'codebaseSize',COALESCE(codebase_size,''),'requiresDocker',requires_docker,'requiresDatabase',requires_database,
      'requiresSecrets',requires_secrets,'requiresGpu',requires_gpu,'readmeExcerpt',COALESCE(readme_excerpt,''))
      FROM beginner_project WHERE status='PUBLISHED' ORDER BY id""")
    for p in projects:
        for k in ['quickStart','requiresDocker','requiresDatabase','requiresSecrets','requiresGpu']:
            p[k] = p[k] == 1
        p['readmeExcerpt'] = p['readmeExcerpt'].encode('utf-16-le')[:12000].decode('utf-16-le', errors='surrogatepass')
    careers = query("""SELECT JSON_OBJECT('id',cp.project_id,'fullName',bp.full_name,
      'description',COALESCE(bp.description_original,''),'language',COALESCE(bp.primary_language,''),
      'techStack',COALESCE(bp.tech_stack,''),'difficulty',cp.difficulty,'overallScore',cp.overall_score,
      'bestRole',cp.best_role_code) FROM career_project_profile cp JOIN beginner_project bp ON bp.id=cp.project_id
      WHERE cp.status='PUBLISHED' ORDER BY cp.project_id""")
    roles = query("""SELECT JSON_OBJECT('projectId',project_id,'roleCode',role_code,'roleRelevance',role_relevance,
      'provableAbility',provable_ability,'knowledgeCoverage',knowledge_coverage,'portfolioCompleteness',portfolio_completeness,
      'interviewValue',interview_value,'totalScore',total_score,'jobSampleCount',job_sample_count,
      'evidenceJson',CAST(evidence_json AS CHAR),'scoreVersion',score_version) FROM career_project_role_score
      ORDER BY total_score DESC,role_code""")
    taxonomy = (ROOT/'orange-server/src/main/java/com/orangejd/server/service/CareerRoleTaxonomy.java').read_text(encoding='utf-8')
    names = dict(re.findall(r'new RoleCategory\((\w+), "([^"]+)"', taxonomy))
    role_fields = ['projectId','roleCode','roleRelevance','provableAbility','knowledgeCoverage','portfolioCompleteness',
                   'interviewValue','totalScore','jobSampleCount','evidenceJson','scoreVersion']
    roles = [{k:r.get(k) for k in role_fields} for r in roles]
    # Match LinkedHashMap field ordering in the existing Java prompt builders.
    beginner_fields = ['id','fullName','description','language','techStack','score','license','quickStart','setupSteps',
                      'codebaseSize','requiresDocker','requiresDatabase','requiresSecrets','requiresGpu','readmeExcerpt']
    projects = [{k:p.get(k) for k in beginner_fields} for p in projects]
    career_fields = ['id','fullName','description','language','techStack','difficulty','overallScore','bestRole']
    careers = [{k:p.get(k) for k in career_fields} for p in careers]
    for p in careers:
        p['bestRole'] = names.get(p['bestRole'], p['bestRole'])
        p['roleScores'] = [r for r in roles if r['projectId'] == p['id']]
    payloads, manifest = [], []
    for kind, rows in [('beginner',projects),('career',careers)]:
        system, prefix, code_hash = prompts(kind)
        ordered = sorted(rows, key=lambda r: len(compact(r)))
        chosen = [ordered[0], ordered[len(ordered)//2], ordered[-1]]
        for label, selection in list(zip(['short','median','long'], [[x] for x in chosen])) + [('batch3', chosen)]:
            messages = [{'role':'system','content':system},{'role':'user','content':prefix+compact(selection)}]
            payloads.append({'name':kind+'-'+label,'kind':kind,'count':len(selection),'ids':[x['id'] for x in selection],
                             'repositories':[x['fullName'] for x in selection], 'body':{'messages':messages,'temperature':0.2}})
        manifest.append({'kind':kind,'population':len(rows),'codeSha256':code_hash,
                         'inputCharacters':[{'id':x['id'],'chars':len(compact(x))} for x in rows]})
    dump(STAGE/'inputs.json',payloads)
    dump(OUT/'manifest.json',{'scope':'Current published report inputs; min/median/max input characters; 3 singles and one batch of 3 per kind; no report writes; not a random sample.',
                             'manifest':manifest,'requests':[{k:v for k,v in p.items() if k!='body'} for p in payloads]})
    print(compact({'prepared':len(payloads),'populations':[(x['kind'],x['population']) for x in manifest]}))

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

def run():
    value = json.loads(subprocess.check_output(['docker','inspect','nxz-bigorange-backend-1'], encoding='utf-8'))[0]
    env = dict(s.split('=',1) for s in value['Config']['Env'] if '=' in s)
    endpoint = (env.get('AI_TEXT_ENDPOINT') or env['AI_TREND_ENDPOINT']).rstrip('/')
    key = env.get('AI_TEXT_KEY') or env['AI_TREND_KEY']
    model = env.get('AI_TEXT_MODEL') or env['AI_TREND_MODEL']
    assert endpoint == 'https://api.deepseek.com' and model == 'deepseek-v4-pro'
    rows = json.loads((STAGE/'inputs.json').read_text(encoding='utf-8'))
    def invoke(p):
        target=OUT/(p['name']+'.json')
        if target.exists(): return
        body = {**p['body'],'model':model}
        result = {k:v for k,v in p.items() if k!='body'}
        result.update(endpoint=endpoint+'/chat/completions',model=model,temperature=0.2,
                      thinking='UNSPECIFIED_AS_CURRENT_REPORT_PATH',maxTokens='UNSPECIFIED_AS_CURRENT_REPORT_PATH',
                      requestSha256=hashlib.sha256(compact(body).encode()).hexdigest())
        start=time.monotonic()
        try:
            request=urllib.request.Request(endpoint+'/chat/completions',data=compact(body).encode(),
                 headers={'Authorization':'Bearer '+key,'Content-Type':'application/json'})
            with urllib.request.build_opener(NoRedirect()).open(request,timeout=120) as response:
                data=json.load(response);result['status']=response.status
            choice=data['choices'][0];content=choice.get('message',{}).get('content') or ''
            result.update(usage=data.get('usage'),finishReason=choice.get('finish_reason'),actualModel=data.get('model'),contentCharacters=len(content))
            try:
                parsed=json.loads(re.sub(r'^```(?:json)?\s*|\s*```$','',content.strip()))
                items=parsed if isinstance(parsed,list) else parsed.get('projects',[])
                result['returnedIds']=[r.get('id') for r in items]
                required=(['descriptionZh','whatIsIt','beginnerReason','modificationIdeas','mainRisks','finalRecommendation']
                          if p['kind']=='beginner' else ['summaryZh','careerValue','proofPoints','interviewTopics','modificationPlan','mainRisks','estimatedDuration'])
                result['completeCount']=sum(all(r.get(f) for f in required) for r in items if r.get('id') in p['ids'])
            except (ValueError,TypeError,AttributeError): result['parseError']=True
            # Never persist provider reasoning, credentials, or headers.
            dump(STAGE/(p['name']+'-visible-output.json'),{'content':content,'usage':data.get('usage')})
        except urllib.error.HTTPError as e:
            result.update(status=e.code,error='PROVIDER_HTTP_ERROR',usage=None)
        except Exception as e:
            result.update(error=type(e).__name__,usage=None)
        result['durationMs']=round((time.monotonic()-start)*1000)
        dump(target,result);print(compact(result),flush=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        list(pool.map(invoke,rows))

if __name__=='__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=['capture','run']);args=parser.parse_args()
    capture() if args.action=='capture' else run()
