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
        for (S s : list(c)) {
            if (s.enabled && !s.instructions.isEmpty()) {
                if (sb.length() > 0) sb.append("\n\n");
                sb.append("[Skill: ").append(s.name).append("]\n").append(s.instructions);
            }
        }
        return sb.toString();
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
        out.add(preset("preset_scaffold", "新项目脚手架", "新建项目时遵循目录/启动脚本/README/依赖声明约定",
                "新建项目时遵循以下约定：\n"
                + "1. 每个项目放在独立目录中，不得散落零散文件。\n"
                + "2. 生成启动脚本 start.sh（首行 #!/data/data/com.termux/files/usr/bin/bash），并赋予可执行权限（chmod +x）。\n"
                + "3. 生成 README.md（说明用途与启动方式）。\n"
                + "4. 依赖用声明式文件管理（package.json / requirements.txt / pyproject.toml 等），不手工散装安装。\n"
                + "5. 完成后按「完工自检」流程验证项目可运行。", false));
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
