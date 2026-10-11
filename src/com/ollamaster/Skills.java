package com.ollamaster;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Skills {
    public static class S {
        public String id, name = "", desc = "", instructions = "";
        public boolean enabled;
    }

    private static File f(Context c) { return new File(c.getFilesDir(), "skills.json"); }

    public static List<S> list(Context c) {
        ArrayList<S> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(ConvStore.read(f(c)));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                S s = new S();
                s.id = o.getString("id");
                s.name = o.optString("name");
                s.desc = o.optString("desc");
                s.instructions = o.optString("instructions");
                s.enabled = o.optBoolean("enabled");
                out.add(s);
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static void saveAll(Context c, List<S> list) {
        String json = toJson(list);
        ConvStore.io(() -> {
            try { ConvStore.write(f(c), json); } catch (Exception ignored) {}
        });
    }

    public static void saveAllSync(Context c, List<S> list) {
        try { ConvStore.write(f(c), toJson(list)); } catch (Exception ignored) {}
    }

    private static String toJson(List<S> list) {
        try {
            JSONArray arr = new JSONArray();
            for (S s : list) {
                JSONObject o = new JSONObject();
                o.put("id", s.id);
                o.put("name", s.name);
                o.put("desc", s.desc);
                o.put("instructions", s.instructions);
                o.put("enabled", s.enabled);
                arr.put(o);
            }
            return arr.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    public static String raw(Context c) {
        try { return ConvStore.read(f(c)); } catch (Exception e) { return "[]"; }
    }

    public static String enabledPrompt(Context c) {
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (S s : list(c)) {
            if (s.enabled && !s.instructions.isEmpty()) {
                if (!any) { sb.append("<skills>\n"); any = true; }
                sb.append("  <skill name=\"").append(s.name).append("\"");
                if (!s.desc.isEmpty()) sb.append(" description=\"").append(s.desc.replace("\"", "")).append("\"");
                sb.append(" />\n");
            }
        }
        if (any) {
            sb.append("</skills>\n");
            sb.append("以上为已注册技能的元数据。当任务匹配某技能时，用 load_skill 工具按名称加载完整指令。");
        }
        return sb.toString();
    }

    /** 按名称加载单个 Skill 的完整指令（渐进式披露第二层） */
    public static String loadSkillDetail(Context c, String name) {
        for (S s : list(c)) {
            if (s.enabled && s.name.equals(name)) {
                return s.instructions;
            }
        }
        return null;
    }

    /** 导出为 SKILL.md 格式（YAML frontmatter + Markdown 正文） */
    public static String toSkillMd(S s) {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("name: ").append(s.name.replaceAll("\\s+", "-").toLowerCase().replaceAll("[^a-z0-9-]", "")).append("\n");
        sb.append("description: ").append(s.desc.replace("\n", " ")).append("\n");
        sb.append("---\n\n");
        sb.append(s.instructions);
        return sb.toString();
    }

    /** 从 SKILL.md 内容解析出 Skill（YAML frontmatter + Markdown 正文） */
    public static S parseSkillMd(String md) {
        S s = new S();
        s.id = ConvStore.newId();
        s.enabled = true;
        if (md == null || md.trim().isEmpty()) return s;
        String text = md.trim();
        if (text.startsWith("---")) {
            int end = text.indexOf("\n---", 3);
            if (end > 0) {
                String fm = text.substring(3, end).trim();
                String body = text.substring(end + 4).trim();
                for (String line : fm.split("\n")) {
                    int colon = line.indexOf(":");
                    if (colon < 0) continue;
                    String key = line.substring(0, colon).trim();
                    String val = line.substring(colon + 1).trim();
                    if (key.equals("name")) s.name = val;
                    else if (key.equals("description")) s.desc = val;
                }
                s.instructions = body;
            } else {
                s.instructions = text;
            }
        } else {
            s.instructions = text;
        }
        if (s.name == null || s.name.isEmpty()) s.name = "unnamed-skill";
        return s;
    }

    public static S blank() {
        S s = new S();
        s.id = ConvStore.newId();
        return s;
    }

    public static List<S> presets() {
        List<S> out = new ArrayList<>();
        out.add(preset("preset_selfcheck", "完工自检", "项目完成后自动执行：语法检查 → 运行 → 测试 → 错误修复循环，全部通过才宣告完成",
                "完成任何代码/项目任务后，不要直接宣告完成，必须按以下顺序自检：\n"
                + "1. 语法检查：先用编译器或语法检查工具验证（如 javac、node --check、python -m py_compile、php -l、shellcheck 等，按项目类型选择），发现语法错误立即修复，重复直到语法全部通过。\n"
                + "2. 语法通过后，再运行项目或入口脚本（优先使用项目自带的 start.sh 或构建脚本），观察启动输出是否正常。\n"
                + "3. 运行可用测试（单元测试或最小功能验证命令）验证核心功能。\n"
                + "4. 任何一步失败：读取完整错误输出，定位根因，修改代码修复，然后从第 1 步重新开始。\n"
                + "5. 只有语法检查、运行、测试全部通过，才可向用户报告完成；报告时附上关键验证输出（如编译通过、测试结果摘要）。", true));
        out.add(preset("preset_selffix", "错误自修复", "工具失败/命令报错时自动分析原因并修复重试，不把错误抛给用户",
                "当工具执行失败、命令报错或测试不通过时，不要把错误原样抛给用户，也不要停下等待指示：\n"
                + "1. 读取并分析完整错误输出，定位根本原因。\n"
                + "2. 用 read_file 查看相关代码，用 write_file/append_file 修复。\n"
                + "3. 修复后重新执行原操作验证是否解决。\n"
                + "4. 最多连续自主修复 5 轮；若仍未解决，汇总已尝试的方案与最后的错误，向用户请求决策。", true));
        out.add(preset("preset_memory", "记忆库", "使用内置记忆库工具（mem_*）跨会话保存、检索与整理重要信息（抽屉式嵌套分类）",
                "你拥有一个「长期记忆库」：记忆按分类抽屉嵌套组织（分类路径用斜杠分层，如 工作/项目/后端，形成多级抽屉）。\n"
                + "1. 保存：用户提到重要信息、偏好、项目事实、长期目标时，用 mem_write 保存。title 简洁概括，content 完整记录，category 归入已有分类路径（如无则新建「大类/子类」层级）。\n"
                + "2. 检索：被问及先前讨论过的内容时，先 mem_search 查记忆库再作答（可用 mem_list 浏览目录、mem_read 读取全文）；回答时可主动说明引用了记忆。\n"
                + "3. 管理：记忆过时或有误时用 mem_update 修改；无用/重复的记忆用 mem_delete 清理；不确定是否已存时先 mem_search 查重。\n"
                + "4. 分类稳定：优先复用已有分类，避免碎片化分类；重要条目可加标签（tags）便于检索。\n"
                + "5. 记忆是跨会话长期资产：用户明确要求记住的内容不可遗漏；可在任务完成摘要中说明记忆库使用情况。", true));
        out.add(preset("preset_img", "生图助手", "用户需要图片/配图/视觉素材时，用 image_generate 工具调用配置的生图 AI 生成图片",
                "你拥有生图能力（image_generate 工具），遵循以下流程：\n"
                + "1. 用户要求生成图片、配图、海报、插画、示意图等视觉内容时，调用 image_generate 工具。\n"
                + "2. prompt 必须写详细：主体、环境、构图、光影、画风、色彩、情绪，越具体效果越好；可用 style 参数附加风格。\n"
                + "3. 若生图返回「未启用/未配置」提示，先告知用户去 设置 → 生图 AI 开启并填写接口地址、密钥、模型。\n"
                + "4. 生成成功后工具返回图片路径，可结合 read_file / web_vision 查看；若需修改可用新的 prompt 再次生成。", false));
        out.add(preset("preset_scaffold", "新项目脚手架", "新建项目时遵循目录/启动脚本/README/依赖声明约定",
                "新建项目时遵循以下约定：\n"
                + "1. 每个项目放在独立目录中，不得散落零散文件。\n"
                + "2. 生成启动脚本 start.sh（首行 #!/data/data/com.termux/files/usr/bin/bash），并赋予可执行权限（chmod +x）。\n"
                + "3. 生成 README.md（说明用途与启动方式）。\n"
                + "4. 依赖用声明式文件管理（package.json / requirements.txt / pyproject.toml 等），不手工散装安装。\n"
                + "5. 完成后按「完工自检」流程验证项目可运行。", false));
        out.add(preset("preset_taskmanager", "任务管理", "多步骤任务先建任务、按步骤推进、随时调整步骤、完成收尾（task_* 工具）",
                "处理任何多步骤、需要跟踪进度的任务时，用任务工具自主管理，不要一次性乱调工具：\n"
                + "1. 复杂任务（预计 2 步以上）先调 task_create 创建任务并拆解 2~10 个可执行步骤（steps 传 JSON 数组，如 [\"勘察\",\"编码\",\"测试\"]）。\n"
                + "2. 创建后系统提示词会自动注入【当前任务】区块（含状态/进度/步骤标记 []待执行 [>]当前 [x]已完成），一律以它为准推进，不要凭记忆。\n"
                + "3. 每完成一步，立即用 task_update action=set_status step_index=N status=COMPLETED 标记，保持步骤状态与真实进度一致；可附 result 写结果摘要。\n"
                + "4. 执行中情况变化时用 task_update 增删改步骤（add_step / remove_step / update_step / rename），任务允许灵活调整，不必推倒重来。\n"
                + "5. 遇到卡点先换工具或调整步骤拆分，不要轻易放弃；确实无法完成才标 FAILED 并写明原因。\n"
                + "6. 全部完成后必须调 task_complete 传完成摘要，禁止只用文字声称完成。\n"
                + "7. 一个会话同时只维护一个活动任务；简单单步请求不必建任务，直接调工具即可。", true));
        out.add(preset("preset_subagent", "子 Agent 委派", "把会产生大量中间输出的子任务交给独立上下文的子 Agent，只回传结论（subagent_run）",
                "当某个子任务会产生大量中间输出（多文件调研、批量文件处理、长命令日志分析、大范围搜索），而主流程只需要结论时，用 subagent_run 委派给独立上下文的子 Agent：\n"
                + "1. task 参数写清目标、范围、期望产出；已知背景（文件路径、已有结论、约束）放进 context，减少子 Agent 重复探索。\n"
                + "2. tools 参数限定子 Agent 可用工具（逗号分隔，如 read_file,run_command,web_search），越精简越省 token；留空则用默认白名单。\n"
                + "3. max_rounds 控制往返轮数（默认 6，上限 12）：调研类给 8~12，单点查询给 3~4，避免空转。\n"
                + "4. 子 Agent 只回传精简结论报告，主会话上下文不会被中间过程撑爆；拿到报告后直接基于结论继续，不必重跑。\n"
                + "5. 一个子任务只委派一次，不要为「再核实一遍」重复开子 Agent；子 Agent 的产出即可作为该步骤的结论。\n"
                + "6. 若用户在设置中关闭了子 Agent 功能，该工具会被拒绝，改用主流程工具直接完成。", true));
        out.add(preset("preset_team", "团队协作", "多角色 AI 群聊协作：评审/方案设计/复杂拆解，产出结构化纪要（team_run）",
                "需要多视角评审、方案设计、复杂拆解时，用 team_run 组建多角色 AI 工作群聊，让角色轮流发言、互相补位挑错，最后汇总成结构化交付物：\n"
                + "1. task 写清协作目标与期望交付物；context 给出背景/约束/已有结论，避免团队重复摸索。\n"
                + "2. mode 选择编排方式：chat=圆桌群聊（默认）/ pipeline=串行流水线 / hierarchical=层级分派 / debate=评审辩论。\n"
                + "3. roles 可自定义角色，格式 `名称:职责;名称:职责`（2~5 个），留空用默认四人团队（协调/设计/执行/审查）。\n"
                + "4. rounds 控制群聊轮次（默认 1，上限 3）；需要充分辩论或反复打磨时设 2~3。\n"
                + "5. 主会话只收到结构化纪要（结论 / 分工与产出 / 风险 / 下一步），中间过程不占上下文；直接基于纪要作答，无需复述过程。\n"
                + "6. 【重要约束】同一目标只组建一次团队；同一会话最多组建 2 次，达到上限后工具会直接拒绝。若本会话已有群聊交付物，直接基于它作答，不得以「再核实一遍」「结果可能不完整」为由重开。\n"
                + "7. 若用户在设置中关闭了团队功能，改用 subagent_run 或主流程工具完成。", true));
        return out;
    }

    private static S preset(String id, String name, String desc, String instructions, boolean enabled) {
        S s = new S();
        s.id = id;
        s.name = name;
        s.desc = desc;
        s.instructions = instructions;
        s.enabled = enabled;
        return s;
    }

    public static boolean seedPresets(Context c) {
        List<S> list = list(c);
        boolean changed = false;
        for (S p : presets()) {
            boolean exists = false;
            for (S s : list) if (p.id.equals(s.id)) { exists = true; break; }
            if (!exists) { list.add(p); changed = true; }
        }
        if (changed) saveAllSync(c, list);
        return changed;
    }
}
