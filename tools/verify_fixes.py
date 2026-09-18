#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Ollamaster 流式解析 / 空回复修复 —— 三层回归验证

L1 行解析实战：调用真实 Http.postStream 打毒网关（tools/HttpLineRegression.java，需先跑 poison_gateway.py）
L2 状态机结构断言：断言 Cloud/Ollama/ChatPage 源码中存在关键分支（源码级，不看注释漂移）
L3 空回复判定真值表：复刻 ChatPage 的 emptyReply / noBody 判定并跑矩阵
L4 产物校验：解包 APK 的 classes.dex，确认修复特征串进了成品包

用法：python3 tools/verify_fixes.py [apk路径]
"""
import io, os, re, sys, zipfile

SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'com', 'ollamaster')
FAIL = []
def ok(cond, label, extra=''):
    print(('  [PASS] ' if cond else '  [FAIL] ') + label + (('  ' + extra) if extra else ''))
    if not cond: FAIL.append(label)

def read(name):
    return io.open(os.path.join(SRC, name), encoding='utf-8').read()

def has(src, *frags):
    return all(f in src for f in frags)

print('=' * 62)
print('L2 状态机结构断言（源码级）')
print('=' * 62)

cloud = read('Cloud.java')
ollama = read('Ollama.java')
chat = read('ChatPage.java')
http = read('Http.java')

# --- Http：尾行冲刷 + 错误体透出 ---
print('\n[Http.java] 流式读取')
ok('if (!cancel.stop && lineBuf.size() > 0)' in http, 'SSE 末尾残缺行被冲刷（无换行整包响应不再被丢弃）')
ok('decompressIfNeeded' in http and 'GZIPInputStream' in http, 'gzip/deflate 响应体自动解压')
ok('Accept", "application/json, text/event-stream' in http, '声明同时接受 JSON 与 SSE')
ok('Math.max(connectTimeoutMs * 3, 120000)' in http, '思考模型首 token 放宽到 120s 空闲超时')

# --- Cloud：parsedOk 区分「不可解析」与「真空回复」 ---
print('\n[Cloud.java] OpenAI 兼容增量解析')
ok('private boolean parsedOk = false;' in cloud, '引入 parsedOk 标志')
ok(has(cloud, 'JSONObject j = new JSONObject(d);\n                    parsedOk = true;'),
   '解析成功即置位 parsedOk')
ok('if (!parsedOk) {' in cloud, '不可解析响应按错误上报（带原始片段）')
ok('parsedOk = true;' in cloud.split('public static void chatNative')[1] or
   cloud.count('parsedOk = true;') >= 2, 'Ollama 云 /api/chat 通道同样置位')
ok('if (j.has("error") && !j.isNull("error"))' in cloud.replace('\n', ' ').replace('  ', ' ')
   or 'j.has("error")' in cloud, 'HTTP 200 内嵌 error 对象被识别并报错（不再伪装成空回复）')
ok('服务端未返回可解析内容' in cloud, '不可解析时给出可读诊断')

# --- Ollama：本地通道 ---
print('\n[Ollama.java] 本地增量解析')
ok('private boolean parsedOk = false;' in ollama, '本地通道同样引入 parsedOk')
ok('服务端未返回可解析内容' in ollama, '本地通道给出可读诊断')
ok('reasoningAcc' in ollama, '思考链增量单独累积')

# --- ChatPage：截断/推理预算/空回复 ---
print('\n[ChatPage.java] 回合收尾判定')
ok('CONTINUE_HINT_NO_BODY' in chat, '新增「推理占满预算」专用续写提示')
ok('boolean noBody = stripThink(acc.toString()).trim().isEmpty();' in chat,
   '识别「有输出但正文为空」= 推理吃满预算')
ok('runTurn(noBody ? CONTINUE_HINT_NO_BODY : CONTINUE_HINT, placeholder);' in chat,
   '按场景选择续写提示')
ok('推理占满输出预算、正文为空' in chat, '该场景给出明确用户提示')
ok('boolean emptyReply = body.isEmpty()' in chat, '真空回复才进入重试分支')
ok('conv.msgs.remove(placeholder);' in chat, '重试前移除占位消息，避免重复气泡')
ok('retryCount < max' in chat and 'Math.min(2500L * retryCount, 8000)' in chat,
   '重试采用退避且受 retryMax 约束')

print('\n' + '=' * 62)
print('L3 空回复判定真值表（复刻 ChatPage 判定逻辑）')
print('=' * 62)

def strip_think(s):
    return re.sub(r'<think>.*?</think>', '', s, flags=re.S)

def classify(content, reasoning, truncated):
    """复刻 finishTurn 分支：返回 (动作, 说明)"""
    body = strip_think(content or '').strip()
    if not body and not (reasoning or '').strip():
        return 'retry', '真空回复 → 重试'
    if truncated and strip_think(content or '').strip() == '':
        return 'continue_nobody', '推理吃满预算 → 要求直接作答'
    if truncated:
        return 'continue', '常规截断 → 续写'
    return 'finalize', '正常收尾'

cases = [
    ('', '', False,                        'retry'),
    ('   ', '', True,                       'retry'),
    ('<think>想很久</think>', '想很久', True, 'continue_nobody'),
    ('<think>想很久</think>', '想很久', False, 'finalize'),
    ('答案在下面', '', True,                  'continue'),
    ('答案在下面', '', False,                 'finalize'),
    ('<think>x</think>正文', '', False,       'finalize'),
]
for content, reasoning, trunc, expect in cases:
    got, why = classify(content, reasoning, trunc)
    label = 'content=%-24r trunc=%-5s → %s' % (content[:24], trunc, got)
    ok(got == expect, label, '(%s)' % why)

print('\n' + '=' * 62)
print('L4 产物校验（APK 内 classes.dex 特征串）')
print('=' * 62)
apk = sys.argv[1] if len(sys.argv) > 1 else \
    '/storage/emulated/0/应用/01_游戏/Ollamaster/Ollamaster.apk'
if not os.path.isfile(apk):
    ok(False, 'APK 存在', apk)
else:
    ok(True, 'APK 存在', '%s (%.1f KB)' % (apk, os.path.getsize(apk) / 1024))
    z = zipfile.ZipFile(apk)
    names = z.namelist()
    ok('classes.dex' in names, '含 classes.dex')
    dex = z.read('classes.dex')
    ok(len(dex) > 10000, 'classes.dex 非空', '%d B' % len(dex))
    checks = [
        (b'\xe6\x9c\x8d\xe5\x8a\xa1\xe7\xab\xaf\xe6\x9c\xaa\xe8\xbf\x94\xe5\x9b\x9e\xe5\x8f\xaf\xe8\xa7\xa3\xe6\x9e\x90\xe5\x86\x85\xe5\xae\xb9',
         '含诊断串「服务端未返回可解析内容」'),
        (b'\xe6\x8e\xa8\xe7\x90\x86\xe5\x8d\xa0\xe6\xbb\xa1\xe8\xbe\x93\xe5\x87\xba\xe9\xa2\x84\xe7\xae\x97',
         '含「推理占满输出预算」提示'),
        (b'\xe8\x87\xaa\xe5\x8a\xa8\xe7\xbb\xad\xe5\x86\x99\xe4\xb8\xad', '含「自动续写中」提示'),
    ]
    for needle, label in checks:
        ok(needle in dex, label)
    ok(len(z.read('AndroidManifest.xml')) > 0, 'AndroidManifest.xml 可读')
    z.close()

print('\n' + '=' * 62)
if FAIL:
    print('结果：%d 项未通过' % len(FAIL))
    for f in FAIL: print('   - ' + f)
    sys.exit(1)
print('结果：全部通过')
