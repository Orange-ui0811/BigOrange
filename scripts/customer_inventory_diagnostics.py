"""Read-only local DB snapshot -> production Java project filters. No external model calls."""
import json
import os
import subprocess
import tempfile
import re
import argparse
from pathlib import Path
from customer_local_smoke import ROOT, sql

def rows(table, fields, where):
    pairs=','.join("'%s',%s" % (key, value) for key,value in fields.items())
    return [json.loads(line) for line in sql(f'SELECT JSON_OBJECT({pairs}) FROM {table} WHERE {where}').splitlines() if line]

parser=argparse.ArgumentParser();parser.add_argument('--report',default='customer-v42-live-counts');args=parser.parse_args()
if not re.fullmatch('[a-zA-Z0-9-]+',args.report): raise SystemExit('Invalid report name')
base={'fullName':'bp.full_name','htmlUrl':'bp.html_url','primaryLanguage':'bp.primary_language','techStack':'bp.tech_stack','archived':'bp.archived','requiresDocker':'bp.requires_docker','requiresDatabase':'bp.requires_database','requiresSecrets':'bp.requires_secrets'}
beginner=rows('beginner_project bp',base|{'id':'bp.id','status':'bp.status','difficulty':'bp.difficulty','estimatedDuration':'bp.estimated_duration','descriptionZh':'bp.description_zh','topicsJson':'CAST(bp.topics_json AS CHAR)','editorialScore':'bp.editorial_score','beginnerScore':'bp.beginner_score','codebaseSize':'bp.codebase_size','whatIsIt':'bp.what_is_it','beginnerReason':'bp.beginner_reason','modificationIdeas':'bp.modification_ideas','mainRisks':'bp.main_risks'},"bp.status IN ('PUBLISHED','CANDIDATE') AND COALESCE(bp.archived,0)=0")
career=rows('career_project_profile cp JOIN beginner_project bp ON bp.id=cp.project_id',base|{'projectId':'cp.project_id','status':'cp.status','difficulty':'cp.difficulty','estimatedDuration':'cp.estimated_duration','summaryZh':'cp.summary_zh','overallScore':'cp.overall_score','careerValue':'cp.career_value','proofPoints':'cp.proof_points','modificationPlan':'cp.modification_plan','mainRisks':'cp.main_risks'},"cp.status IN ('PUBLISHED','CANDIDATE') AND bp.status NOT IN ('HIDDEN','INVALID') AND COALESCE(bp.archived,0)=0")
source=(ROOT/'orange-server/src/main/java/com/orangejd/server/customer/CustomerCatalogMapper.java').read_text(encoding='utf-8')
external_query=re.search(r'@Select\("""\s*(SELECT d\.id.*?)"""\)\s*List<Map<String,Object>> externalDocuments',source,re.S).group(1)
external=[dict(id=int(parts[0]),source_ref=parts[1],title=parts[2],active_version_id=int(parts[3])) for line in sql(external_query).splitlines() if (parts:=line.split('\t')) and len(parts)==4]
with tempfile.NamedTemporaryFile(mode='w',suffix='.json',prefix='orange-customer-inventory-',encoding='utf-8',delete=False) as f:
    json.dump(dict(beginner=beginner,career=career,external=external),f,ensure_ascii=False)
    snapshot=Path(f.name)
try:
    env=os.environ.copy();env.update(RUN_CUSTOMER_INVENTORY='true',CUSTOMER_INVENTORY_SNAPSHOT=str(snapshot),CUSTOMER_INVENTORY_REPORT=args.report)
    with (ROOT/f'docs/customer/acceptance/{args.report}-tests.log').open('w',encoding='utf-8') as log:
        result=subprocess.run(['mvn.cmd','-B','-pl','orange-server','-am','-Dtest=CustomerInventoryIntegrationTest','-Dsurefire.failIfNoSpecifiedTests=false','test'],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
    print('Local DB / Java diagnostics exit:',result.returncode)
finally:
    snapshot.unlink(missing_ok=True)
raise SystemExit(result.returncode)
