import subprocess,json,collections,pathlib
out=pathlib.Path('orange-server/target/rag-audit')
def run(args):
 p=subprocess.run(args,capture_output=True,encoding='utf-8',errors='replace',check=True);return p.stdout
lua='''local cursor="0"; local rows={}; repeat local r=redis.call("SCAN",cursor,"MATCH","default:*","COUNT",200); cursor=r[1]; for _,k in ipairs(r[2]) do local v=cjson.decode(redis.call("JSON.GET",k)); local x={key=k}; for _,f in ipairs({"docId","source","projectId","projectType","mappingStatus","docTitle","sourceRef","chunkIndex","difficulty","duration","techStack"}) do x[f]=v[f] end; rows[#rows+1]=x end until cursor=="0"; return cjson.encode(rows)'''
raw=json.loads(run(['docker','exec','nxz-bigorange-redis-1','redis-cli','--json','EVAL',lua,'0']))
rows=json.loads(raw)
sql='SELECT id,source,chunk_count,vector_status FROM knowledge_document ORDER BY id;'
cmd='MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" -N -e '+"'"+sql+"'"
db=[s.split('\t') for s in run(['docker','exec','nxz-bigorange-mysql-1','sh','-c',cmd]).splitlines()]
byid=collections.Counter(str(r.get('docId')) for r in rows)
ids={r[0] for r in db}
summary={'dbDocuments':len(db),'dbChunks':sum(int(r[2]) for r in db),'redisChunks':len(rows),'redisBySource':dict(collections.Counter(r.get('source','MISSING') for r in rows)),'missingDocId':sum('docId' not in r for r in rows),'orphanChunks':sum(str(r.get('docId')) not in ids for r in rows),'mismatches':[{'docId':r[0],'source':r[1],'status':r[3],'dbChunks':int(r[2]),'redisChunks':byid[r[0]]} for r in db if int(r[2])!=byid[r[0]]],'bySourceMetadata':{},'manual':[r for r in rows if r.get('source')=='MANUAL']}
for source in summary['redisBySource']:
 rs=[r for r in rows if r.get('source','MISSING')==source];summary['bySourceMetadata'][source]={'chunks':len(rs),'withProjectId':sum(bool(r.get('projectId')) for r in rs),'withProjectType':sum(bool(r.get('projectType')) for r in rs),'mappingStatus':dict(collections.Counter(r.get('mappingStatus','MISSING') for r in rs))}
(out/'inventory.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(summary,ensure_ascii=False))
