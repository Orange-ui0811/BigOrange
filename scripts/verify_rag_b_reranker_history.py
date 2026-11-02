"""Verify the unchanged v1 experiment against its archived source, independently of today's code."""
import hashlib,json,pathlib,shutil,subprocess,sys,tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/rag-b/reranker-v1'
def read(p):return json.loads(p.read_text(encoding='utf8'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    files={}
    for name in ['seed-manifest.json','request-manifest.json','completion-seal.json']:
        for p,h in read(OUT/name)['files'].items():
            assert p not in files or files[p]==h,p;files[p]=h
    mapping=read(ROOT/'docs/rag-b/reranker-integration-v1/history-sources/mapping.json')['files']
    with tempfile.TemporaryDirectory(prefix='orangejd-rerank-history-') as folder:
        replay=pathlib.Path(folder)
        for p,h in files.items():
            source=ROOT/p
            if sha(source)!=h:
                old=mapping[p];source=ROOT/old['archive'];assert old['sha256']==h
            assert sha(source)==h,p
            dest=replay/p;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,dest)
        # Completion seal itself is not a member of its own file list.
        dest=replay/'docs/rag-b/reranker-v1/completion-seal.json';shutil.copyfile(OUT/'completion-seal.json',dest)
        subprocess.run([sys.executable,str(replay/'scripts/rag_b_reranker_eval.py'),'verify'],check=True,cwd=replay)
    print(json.dumps({'status':'HISTORICAL_RERANK_V1_VERIFIED','archivedSourceFiles':len(mapping),'newModelCalls':0}))
if __name__=='__main__':main()
