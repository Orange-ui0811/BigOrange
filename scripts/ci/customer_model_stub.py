"""Loopback-only deterministic provider for the disposable customer HTTP smoke."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json

class Handler(BaseHTTPRequestHandler):
    requests=0
    def log_message(self,*args):pass
    def do_POST(self):
        if self.headers.get('Transfer-Encoding','').lower()=='chunked':
            while True:
                size=int(self.rfile.readline().split(b';',1)[0],16)
                if not size:
                    while self.rfile.readline().strip():pass
                    break
                self.rfile.read(size);self.rfile.read(2)
        else:self.rfile.read(int(self.headers.get('Content-Length','0')))
        if self.path!='/v1/chat/completions':
            self.send_error(404);return
        Handler.requests+=1
        action=json.dumps({'intent':'CHAT','reply':'你好，这是隔离环境的固定验收回复。','changes':{}},ensure_ascii=False)
        body=json.dumps({'id':'chatcmpl-synthetic','object':'chat.completion','created':0,'model':'synthetic',
            'choices':[{'index':0,'message':{'role':'assistant','content':action},'finish_reason':'stop'}],
            'usage':{'prompt_tokens':20,'completion_tokens':20,'total_tokens':40}}).encode()
        self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
    def do_GET(self):
        self.send_response(200);self.send_header('Content-Type','application/json');self.end_headers()
        self.wfile.write(json.dumps({'requests':Handler.requests}).encode())

if __name__=='__main__':ThreadingHTTPServer(('127.0.0.1',18999),Handler).serve_forever()
