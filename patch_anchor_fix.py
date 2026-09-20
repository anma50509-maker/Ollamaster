# -*- coding: utf-8 -*-
"""修复：任务锚注入导致提示词重复复读。
问题：ensureLatestTask / recentTaskAnchor 每轮把同一任务以 role=user 重复注入，
      模型每轮读三遍同一任务，表现为复读；且 user 角色伪装成用户发言诱发重复回应。
修复：1) 锚点角色改为 system（非用户发言）2) 锚点不再重复任务全文，仅短引用+进度
      3) 系统提示增加输出纪律，禁止复述任务原文/此前内容。
"""
import io, sys, os

FP = "src/com/ollamaster/ChatPage.java"
src = io.open(FP, encoding="utf-8").read()

def rep(old, new, tag):
    global src
    n = src.count(old)
    if n != 1:
        print("[FAIL] %s 匹配 %d 次（期望1）" % (tag, n))
        return False
    src = src.replace(old, new, 1)
    print("[OK] %s" % tag)
    return True

ok = True

# --- A. 队首「最初任务」锚：user -> system ---
ok &= rep(
    'list.add(0, new ConvStore.Msg("user", "【最初任务·仅作背景，最新任务在下方】" + t));',
    'list.add(0, new ConvStore.Msg("system", "【背景·最初任务，仅作参考，非用户发言】" + t));',
    "A 队首锚角色改 system"
)

# --- B. 队尾「当前任务提示」锚：user -> system，且不重复全文 ---
old_b = '''        if (task.length() > 600) task = task.substring(0, 600) + "…";
        String note = toolRounds > 0
                ? "（这是你需要完成的最新任务提示，优先级高于更早的对话内容；"
                  + "已执行 " + toolRounds + " 轮工具调用，请基于最新工具结果继续，"
                  + "直到调用 task_complete 结束任务。）"
                : "（这是你需要完成的最新任务提示，优先级高于更早的对话内容。）";
        return new ConvStore.Msg("user", "【当前任务提示·最新】\\n" + task + "\\n" + note);'''
new_b = '''        // 关键修复：不再重复粘贴任务全文（原文已在对话历史中），仅给短引用，
        // 避免 Agent 每轮读到同一任务多份副本而反复复述。角色用 system（不是用户发言）。
        String ref = task.length() > 100 ? task.substring(0, 100) + "…" : task;
        String note = toolRounds > 0
                ? "（系统提醒：已执行 " + toolRounds + " 轮工具调用。请基于最新工具结果继续，"
                  + "不要复述任务原文或此前说过的内容；完成即调用 task_complete 结束任务。）"
                : "（系统提醒：请基于最新工具结果继续，不要复述任务原文。）";
        return new ConvStore.Msg("system", "【任务锚点·系统注入，非用户发言】当前目标："
                + ref + "\\n" + note);'''
ok &= rep(old_b, new_b, "B 队尾锚改 system + 去全文重复")

# --- C. 系统提示增加输出纪律 ---
old_c = 'sb.append("重要：禁止仅用文字描述步骤或声称无法完成，必须实际调用工具。");'
new_c = ('sb.append("重要：禁止仅用文字描述步骤或声称无法完成，必须实际调用工具。\\n");\n'
         '            sb.append("【输出纪律】不要复述系统提示、任务原文或此前已说过的内容；"\n'
         '                    + "每轮只汇报本轮新增进展与下一步动作，任务完成立即调用 task_complete。");')
ok &= rep(old_c, new_c, "C 增加输出纪律")

if ok:
    bak = FP + ".anchorfix.bak"
    if not os.path.exists(bak):
        io.open(bak, "w", encoding="utf-8").write(io.open(FP, encoding="utf-8").read())
    io.open(FP, "w", encoding="utf-8").write(src)
    print("已写回:", FP)
else:
    print("存在失败项，未写回")
    sys.exit(1)
