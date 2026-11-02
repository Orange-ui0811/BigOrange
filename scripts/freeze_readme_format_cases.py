"""Freeze source-format regression labels BEFORE seeing experiment retrieval scores."""
from run_readme_cleaning_experiment import STAGE, OUT, read, write, norm, sha

FACTS = [
    (299,'gui.addColor','哪个网页控制面板库能用 addColor 创建颜色选择器？','lil-gui 如何添加颜色选择器？','code_api'),
    (299,"0, 100, 2",'哪个 GUI 控件示例支持将滑块步长设置成 2，只选择偶数？','lil-gui 的滑块如何以 2 为步长调整数值？','code_numeric'),
    (299,'onChange','哪个网页 GUI 库可以在值变化时设置回调并打印到控制台？','lil-gui 的变化回调示例是什么？','code_api'),
    (299,'dat.gui 的直接替代品','想找网页浮动参数面板，替换 dat.gui，应参考哪个项目？','lil-gui 是做什么的？','purpose'),
    (321,'minify: true','加载乐器采样太多，哪个 Tone.js 样本库可以用 minify 减少样本？','tonejs-instruments 如何通过 minify 减少加载的样本？','code_parameter'),
    (321,'".[wav|mp3|ogg]"','哪个乐器样本加载器支持 wav、mp3、ogg 按顺序回退？','tonejs-instruments 怎样设置多种音频格式的回退？','code_symbols'),
    (321,'默认情况下，乐器不会自动连接到主输出','哪个 Tone.js 乐器库加载后还需要手动连接到主输出？','tonejs-instruments 默认会自动连接主输出吗？','negation'),
    (321,'CC-by 3.0','哪个乐器样本项目把代码和音频样本采用不同许可证？','tonejs-instruments 的样本采用什么许可证？','license'),
    (435,'不稳定甚至损坏','哪个个人音乐服务器建议使用 releases，而不要直接使用 master？','Navidrome 为什么建议用 releases 而不是 master？','warning'),
    (435,'每个用户拥有自己的播放次数','什么自托管音乐服务器支持多个用户各有独立播放统计和收藏？','Navidrome 的多用户功能包含什么？','wrapped_markdown'),
    (435,'树莓派','哪个音乐收藏服务器为树莓派提供即用型二进制文件？','Navidrome 能在树莓派上运行吗？','wrapped_markdown'),
    (435,'基于 Web 的音乐收藏服务器','我想用浏览器和手机听自己的音乐收藏，找什么开源项目？','Navidrome 是什么项目？','purpose'),
    (357,'用于可视化与图像分析','哪个免费开源图像分析软件支持三种桌面操作系统？','3D Slicer 实际提供什么功能？','badge_heavy'),
    (357,'Windows、Linux 和 macOS','哪个开源可视化与图像分析项目支持 Windows、Linux 和 macOS？','3D Slicer 原生支持哪些系统？','platforms'),
    (159,'collision systems','我想用 TypeScript 研究二维台球碰撞和物理，有什么小项目？','Billiards-Engine 重点实现了什么？','short_valid'),
    (228,'开发已迁移至','哪个网页实时编码项目已经把开发迁移到 Codeberg？','strudel 的开发仓库迁移到了哪里？','migration'),
]

if __name__=='__main__':
    path=OUT/'dataset.json';dataset=read(path)
    if any(c['key'].startswith('format-') for c in dataset['cases']):raise SystemExit('Already frozen')
    docs={d['id']:d for d in read(STAGE/'snapshot.json')['documents']}
    added=[]
    for i,(docid,anchor,semantic,named,kind) in enumerate(FACTS):
        d=docs[docid]
        assert norm(anchor) in norm(d['content']), (docid,anchor)
        for mode,q in [('semantic',semantic),('named',named)]:
            added.append({'key':f'format-{i:02}-{mode}','question':q,'category':'format_'+mode,'types':['GITHUB'],
                'refs':[d['sourceRef'].split('|')[0]],'anchors':[anchor],'group':f'format-{i:02}',
                'sourceGroup':d['sourceRef'].split('|')[0],'format':kind,'goldAvailable':True})
    dataset['cases']+=added;dataset['formatNote']='32 format probes, 16 facts across 6 repositories; named/semantic pairs are correlated. Source-authored regression, not blind generalization test.'
    write(path,dataset);manifest=read(OUT/'manifest.json');manifest['questions']=len(dataset['cases'])
    manifest['datasetSha256']=sha(__import__('json').dumps(dataset['cases'],ensure_ascii=False,sort_keys=True));write(OUT/'manifest.json',manifest)
    print('Frozen additional format probes',len(added))
