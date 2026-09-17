package com.ollamaster;

import android.os.Environment;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class LocalTools {

    public static JSONArray specs() {
        JSONArray out = new JSONArray();
        try {
            out.put(fn("list_files", "列出目录下的文件与文件夹",
                    new String[]{"path"}, "目录路径，相对工作区或绝对路径，空为工作区根目录", null));
            out.put(fn("read_file", "读取文本文件内容",
                    new String[]{"path"}, "文件路径", new String[]{"path"}));
            out.put(fn("write_file", "创建或覆盖写入文本文件（自动创建父目录）",
                    new String[]{"path", "content"}, "内容", new String[]{"path", "content"}));
            out.put(fn("append_file", "向文件末尾追加内容",
                    new String[]{"path", "content"}, "追加的内容", new String[]{"path", "content"}));
            out.put(fn("delete_path", "删除文件或整个文件夹（谨慎）",
                    new String[]{"path"}, "路径", new String[]{"path"}));
            out.put(fn("make_dir", "创建文件夹（含父目录）",
                    new String[]{"path"}, "文件夹路径", new String[]{"path"}));
            out.put(fn("run_command", "在工作区目录执行 shell 命令并返回输出（超时60秒）",
                    new String[]{"command"}, "命令行", new String[]{"command"}));
            out.put(fn("web_fetch", "抓取网页并转为纯文本返回",
                    new String[]{"url"}, "完整 URL", new String[]{"url"}));
            out.put(fn("web_open", "在内置浏览器中打开网页供用户查看",
                    new String[]{"url"}, "完整 URL", new String[]{"url"}));
            out.put(fn2("create_skill", "创建或更新一个 AI Skill（技能指令，启用后作为系统指令注入每次对话）。同名覆盖",
                    new String[]{"name", "desc", "instructions", "enabled"},
                    new String[]{"技能名称（唯一标识，同名则更新）", "一句话简介",
                            "技能的具体指令内容，多行文本，描述 AI 应遵循的工作流程",
                            "是否立即启用，true/false，默认 true"},
                    new String[]{"name", "instructions"}));
            out.put(fn2("delete_skill", "按名称删除一个 AI Skill",
                    new String[]{"name"}, new String[]{"要删除的技能名称"}, new String[]{"name"}));
            out.put(fn2("list_skills", "列出所有 AI Skill 及其启用状态",
                    new String[]{}, new String[]{}, null));
            out.put(fn2("load_skill", "按名称加载已启用 Skill 的完整指令内容（渐进式披露第二层）。系统提示词只注入技能元数据，需完整指令时调用此工具",
                    new String[]{"name"}, new String[]{"要加载的技能名称"}, new String[]{"name"}));
            out.put(fn2("create_mcp", "创建或更新一个 MCP 服务器配置（Streamable HTTP），保存后立即尝试连接并发现工具。同名覆盖",
                    new String[]{"name", "url", "headers_json", "enabled"},
                    new String[]{"服务器名称（唯一标识，同名则更新）", "MCP 端点 URL，如 https://example.com/mcp",
                            "可选，自定义请求头 JSON 对象，如 {\"Authorization\":\"Bearer xx\"}",
                            "是否启用，true/false，默认 true"},
                    new String[]{"name", "url"}));
            out.put(fn2("delete_mcp", "按名称删除一个 MCP 服务器配置",
                    new String[]{"name"}, new String[]{"要删除的服务器名称"}, new String[]{"name"}));
            out.put(fn2("task_complete", "标记任务已完成，传入完成摘要。调用后停止工具循环",
                    new String[]{"summary"}, new String[]{"任务完成摘要，简述做了什么、结果如何"}, new String[]{"summary"}));
            out.put(fn2("install_plugin", "安装或更新一个热插拔插件。插件可以定义自定义工具、自定义UI页面、自定义技能和人设卡，安装后立即生效。同名（同id）覆盖",
                    new String[]{"json"},
                    new String[]{"插件 JSON 定义，包含 id/name/desc/tools[]/pages[]/skills[]/personas[] 等字段"},
                    new String[]{"json"}));
            out.put(fn2("uninstall_plugin", "卸载一个已安装的插件，移除其所有工具、页面、技能和人设卡",
                    new String[]{"id"}, new String[]{"要卸载的插件 id"}, new String[]{"id"}));
            out.put(fn2("list_plugins", "列出所有已安装的插件及其状态",
                    null, null, null));
            out.put(fn2("enable_plugin", "启用一个插件，使其工具/页面/技能/人设卡生效",
                    new String[]{"id"}, new String[]{"插件 id"}, new String[]{"id"}));
            out.put(fn2("disable_plugin", "禁用一个插件，暂停其所有功能但保留安装",
                    new String[]{"id"}, new String[]{"插件 id"}, new String[]{"id"}));
            out.put(fn2("create_persona", "创建或更新一张人设卡。AI 直接写入人设库，用户在人设栏立即可见、可编辑可删除；name 已存在则覆盖更新。emoji 参数禁止传 emoji 符号，留空即可（界面用矢量头像兜底）",
                    new String[]{"name", "emoji", "desc", "prompt"},
                    new String[]{"人设卡名称（必填，同名覆盖）", "图标（留空；禁止 emoji）", "一句话简介", "系统提示词（人设核心，塑造性格与专长）"},
                    new String[]{"name"}));
            out.put(fn2("list_personas", "列出所有人设卡（含来源标记：内置/自建/来自插件·只读）",
                    null, null, null));
            out.put(fn2("delete_persona", "删除一张自建或内置人设卡（插件提供的只读卡不可删除）",
                    new String[]{"name", "id"},
                    new String[]{"人设卡名称（与 id 二选一）", "人设卡 id（与 name 二选一）"},
                    null));
        out.put(fn2("mem_list", "列出记忆库条目目录（按 id/标题/分类/更新时间），可用分类过滤",
                new String[]{"category"}, new String[]{"分类路径（如 工作/后端），空则列出全部"}, null));
        out.put(fn2("mem_read", "读取一条记忆的完整内容（按 id）",
                new String[]{"id"}, new String[]{"记忆条目 id（mem_list/mem_search 获取）"}, new String[]{"id"}));
        out.put(fn2("mem_search", "按关键词搜索记忆库（匹配标题/内容/标签/分类），返回匹配条目列表",
                new String[]{"query"}, new String[]{"搜索关键词"}, new String[]{"query"}));
        out.put(fn2("mem_write", "保存一条记忆（title 已存在则覆盖更新）。category 用「大类/子类」斜杠分层实现抽屉嵌套",
                new String[]{"title", "content", "category", "tags"},
                new String[]{"标题（必填，简洁概括）", "内容（完整记录）", "分类路径，如 工作/项目/后端（可空）", "标签，逗号分隔（可选）"},
                new String[]{"title", "content"}));
        out.put(fn2("mem_update", "更新一条已有记忆（只更新传入的字段；无 id 时按 title 匹配）",
                new String[]{"id", "title", "content", "category", "tags"},
                new String[]{"要更新的记忆 id", "新标题", "新内容", "新分类路径", "新标签"},
                new String[]{"id"}));
        out.put(fn2("mem_delete", "删除一条记忆（按 id）",
                new String[]{"id"}, new String[]{"要删除的记忆 id"}, new String[]{"id"}));
        out.put(fn2("mem_stats", "统计记忆库规模（总条数/分类抽屉数/分类树/最近更新时间）",
                null, null, null));
        out.put(fn2("tts_speak", "使用语音合成朗读文本（系统引擎或已配置的第三方 TTS API，中文英文均可）。返回朗读状态",
                new String[]{"text"}, new String[]{"要朗读的文本内容"}, new String[]{"text"}));
        out.put(fn2("tts_stop", "停止当前正在进行的语音朗读",
                null, null, null));
        out.put(fn2("list_settings", "列出应用所有可配置设置项及其当前值（AI 自行配置入口）",
                null, null, null));
        out.put(fn2("get_setting", "读取单个设置项的当前值",
                new String[]{"key"}, new String[]{"设置键名，如 temperature / ttsMode / autoTts"}, new String[]{"key"}));
        out.put(fn2("set_setting", "修改应用设置（AI 自行配置入口）。支持键：host,hosts,port,timeoutSec,retryMax,editMode,themeName,customTheme,cBg,cAccent,cText,fontScale,stream,showThink,streamDiag,temperature,topP,maxTokens,ctxMsgs,summaryKb,sysPrompt,cloudMode,cloudUrl,cloudKey,cloudModels,activeModel,activeCloudModel,ttsMode,ttsUrl,ttsKey,ttsModel,ttsVoice,ttsSpeed,autoTts,activeKeyIndex,apiKeyPool,workspace,imgEnabled,imgUrl,imgKey,imgModel,imgSize,imgStyle,imgDir,imgVisionModel,visionMode,visionUrl,visionKey,visionModelId,autoTitle",
                new String[]{"key", "value"},
                new String[]{"设置键名（见描述）", "设置值：布尔用 true/false，数字用数值，字符串直接填写"},
                new String[]{"key", "value"}));
        out.put(fn2("key_pool_list", "列出 API 密钥池（各服务商 name/id/url/models，key 已脱敏），供 AI 了解可用服务商",
                null, null, null));
        out.put(fn2("key_pool_add", "向密钥池添加一个新服务商密钥。APIKey 遵循只写不读：AI 可写入 key 明文，但读取时永远脱敏",
                new String[]{"name", "url", "key", "models"},
                new String[]{"服务商名称（必填，如 魔塔/硅基流动）", "OpenAI 兼容接口地址（必填，如 https://api.example.com/v1）", "API 密钥（必填）", "可用模型列表（逗号分隔，可空）"},
                new String[]{"name", "url", "key"}));
        out.put(fn2("key_pool_remove", "从密钥池删除一个服务商（按 name 或 id 定位）",
                new String[]{"name", "id"},
                new String[]{"服务商名称（与 id 二选一）", "服务商 id（与 name 二选一，key_pool_list 可查）"},
                null));
        out.put(fn2("web_search", "联网搜索：向搜索引擎提交关键词，返回结果列表（标题/链接/摘要）。用于查资料、找答案、了解实时信息；搜索后如需要可再用 web_fetch 打开具体链接看全文",
                new String[]{"query", "max"},
                new String[]{"搜索关键词（必填，尽量具体，可用引号精确匹配）", "最多返回条数，默认 8，上限 10"},
                new String[]{"query"}));
        out.put(fn2("key_pool_update", "更新密钥池中某个服务商的 key/url/models/newName（按 name 或 id 定位）",
                new String[]{"name", "id", "key", "url", "models", "newName"},
                new String[]{"服务商名称（与 id 二选一）", "服务商 id（与 name 二选一）", "新 API 密钥（可选）", "新接口地址（可选）", "新模型列表（逗号分隔，可选）", "新名称（可选）"},
                null));
        out.put(fn2("browser_open", "在应用内浏览器打开网页并等待加载完成（自动切换到浏览器页），返回标题",
                new String[]{"url", "waitMs"},
                new String[]{"网址（自动补全 https://）", "等待加载毫秒数，默认8000"},
                new String[]{"url"}));
        out.put(fn2("browser_status", "查看浏览器当前页面 URL 与标题",
                new String[]{}, new String[]{}, null));
        out.put(fn2("browser_extract", "提取浏览器当前页面可见文本（自动去标签压缩空白）",
                new String[]{"maxChars"}, new String[]{"最多返回字符数，默认2500"}, null));
        out.put(fn2("browser_click", "模拟鼠标点击页面元素：selector(CSS选择器) 或 x/y 坐标；自动滚动到元素并派发 mousedown/mouseup/click",
                new String[]{"selector", "x", "y"},
                new String[]{"CSS 选择器（与坐标二选一）", "点击 X 坐标", "点击 Y 坐标"},
                null));
        out.put(fn2("browser_type", "模拟键盘在输入框输入文本（自动 focus 并触发 input/change 事件）",
                new String[]{"selector", "text"},
                new String[]{"输入框 CSS 选择器", "要输入的文本"},
                new String[]{"selector", "text"}));
        out.put(fn2("browser_scroll", "滚动页面：direction=top/bottom/up/down/left/right；px 为步长（默认480）",
                new String[]{"direction", "px"},
                new String[]{"滚动方向", "滚动像素（配合 up/down/left/right）"},
                null));
        out.put(fn2("browser_back", "浏览器后退一页",
                new String[]{}, new String[]{}, null));
        out.put(fn2("browser_eval", "在浏览器当前页面执行任意 JavaScript 并返回结果",
                new String[]{"js"}, new String[]{"要执行的 JS 代码"}, new String[]{"js"}));
        out.put(fn2("browser_screenshot", "截取浏览器当前画面保存为 PNG（工作区 browsershots/），返回文件路径；可用 web_vision 分析",
                new String[]{"path"}, new String[]{"可选：保存文件名（默认自动时间戳）"}, null));
        out.put(fn2("browser_ua", "设置浏览器 User-Agent（反爬虫对抗；默认已伪装真实 Chrome 移动端 UA）",
                new String[]{"ua"}, new String[]{"自定义 UA 字符串"}, null));
        out.put(fn2("web_vision", "把截图/图片交给视觉模型理解（本地 Ollama 视觉模型或云端视觉接口），返回描述与关键元素建议坐标",
                new String[]{"path", "question"},
                new String[]{"图片文件路径（browser_screenshot 的输出）", "要问的问题（可要求给出可点击元素坐标）"},
                new String[]{"path"}));
        out.put(fn2("image_generate", "AI 自主生图：调用设置中配置的生图 AI（OpenAI 兼容 images/generations 接口）生成图片，保存到工作区 images/ 目录并返回文件路径。生成前先确认已配置生图接口（设置→生图 AI），未配置则返回提示",
                new String[]{"prompt", "size", "style", "out"},
                new String[]{"图片描述 prompt（必填，尽量详细：主体、环境、构图、光影、画风）", "尺寸，如 1024x1024 / 512x512 / 768x1024，留空用默认", "风格附加词（如 赛博朋克、水彩、写实摄影），附加到 prompt 尾部，可空", "输出文件名（可空，默认自动时间戳 gen_xxx.png）"},
                new String[]{"prompt"}));
        out.put(fn2("rename_conv", "AI 自主会话命名：为当前会话设置一个有意义的标题（如「修 bug」「写简历」「数据分析」），简洁中文，不超过 18 字。仅当前会话生效",
                new String[]{"title"},
                new String[]{"新会话标题，简洁中文，不超过 18 字"},
                new String[]{"title"}));

        } catch (Exception ignored) {}
        return out;
    }

    private static JSONObject fn(String name, String desc, String[] props,
                                 String contentDesc, String[] required) {
        try {
            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            JSONObject properties = new JSONObject();
            for (String pr : props) {
                JSONObject pd = new JSONObject();
                pd.put("type", "string");
                if (contentDesc != null && pr.equals("content")) pd.put("description", contentDesc);
                else pd.put("description", pr.equals("url") ? "URL" : "路径/参数");
                properties.put(pr, pd);
            }
            parameters.put("properties", properties);
            if (required != null && required.length > 0) {
                JSONArray req = new JSONArray();
                for (String r : required) req.put(r);
                parameters.put("required", req);
            }
            JSONObject f = new JSONObject();
            f.put("name", name);
            f.put("description", desc);
            f.put("parameters", parameters);
            JSONObject w = new JSONObject();
            w.put("type", "function");
            w.put("function", f);
            return w;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static JSONObject fn2(String name, String desc, String[] props,
                                   String[] descs, String[] required) {
        try {
            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            JSONObject properties = new JSONObject();
            if (props != null) {
                for (int i = 0; i < props.length; i++) {
                    JSONObject pd = new JSONObject();
                    pd.put("type", "string");
                    pd.put("description", descs != null && i < descs.length ? descs[i] : "参数");
                    properties.put(props[i], pd);
                }
            }
            parameters.put("properties", properties);
            if (required != null && required.length > 0) {
                JSONArray req = new JSONArray();
                for (String r : required) req.put(r);
                parameters.put("required", req);
            }
            JSONObject f = new JSONObject();
            f.put("name", name);
            f.put("description", desc);
            f.put("parameters", parameters);
            JSONObject w = new JSONObject();
            w.put("type", "function");
            w.put("function", f);
            return w;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static boolean has(String name) {
        switch (name) {
            case "list_files": case "read_file": case "write_file": case "append_file":
            case "delete_path": case "make_dir": case "run_command":
            case "web_fetch": case "web_open":
            case "create_skill": case "delete_skill": case "list_skills": case "load_skill":
            case "create_mcp": case "delete_mcp": case "task_complete":
            case "install_plugin": case "uninstall_plugin":
            case "list_plugins": case "enable_plugin": case "disable_plugin":
            case "create_persona": case "list_personas": case "delete_persona":
            case "mem_list": case "mem_read": case "mem_search":
            case "mem_write": case "mem_update": case "mem_delete": case "mem_stats":
            case "tts_speak": case "tts_stop":
            case "list_settings": case "get_setting": case "set_setting":
            case "key_pool_list": case "key_pool_add": case "key_pool_remove": case "key_pool_update":
            case "web_search":
            case "browser_open": case "browser_status": case "browser_extract":
            case "browser_click": case "browser_type": case "browser_scroll":
            case "browser_back": case "browser_eval": case "browser_screenshot":
            case "browser_ua": case "web_vision":
            case "image_generate": case "rename_conv":
                return true;
            default:
                return false;
        }
    }

    public static String call(String name, JSONObject args) throws Exception {
        if (args == null) args = new JSONObject();
        switch (name) {
            case "list_files": return listFiles(resolve(args.optString("path", "")));
            case "read_file": return readFile(resolve(args.getString("path")));
            case "write_file": return writeFile(args.getString("path"), args.optString("content", ""), false);
            case "append_file": return writeFile(args.getString("path"), args.optString("content", ""), true);
            case "delete_path": return deletePath(resolve(args.getString("path")));
            case "make_dir": return makeDir(resolve(args.getString("path")));
            case "run_command": return runCommand(args.getString("command"));
            case "web_fetch": return webFetch(args.getString("url"));
            case "web_open": return webOpen(args.getString("url"));
            case "create_skill": return createSkill(args);
            case "delete_skill": return deleteSkill(args);
            case "list_skills": return listSkills();
            case "load_skill": return loadSkill(args);
            case "create_mcp": return createMcp(args);
            case "delete_mcp": return deleteMcp(args);
            case "task_complete": return "任务已完成：" + args.optString("summary", "无摘要");
            case "install_plugin": return installPlugin(args);
            case "uninstall_plugin": return uninstallPlugin(args);
            case "list_plugins": return listPlugins();
            case "enable_plugin": return enablePlugin(args);
            case "disable_plugin": return disablePlugin(args);
            case "create_persona": return createPersona(args);
            case "list_personas": return listPersonas();
            case "delete_persona": return deletePersona(args);
            case "mem_list": return MemoryStore.listText(args.optString("category", ""));
            case "mem_read": return MemoryStore.readText(args.getString("id"));
            case "mem_search": return MemoryStore.searchText(args.getString("query"));
            case "mem_write": return MemoryStore.write(args);
            case "mem_update": return MemoryStore.update(args);
            case "mem_delete": return MemoryStore.remove(args.getString("id"));
            case "mem_stats": return MemoryStore.statsText();
            case "tts_speak": return ttsSpeak(args.optString("text", ""));
            case "tts_stop": { TtsEngine.get(App.inst).stop(); return "已停止朗读"; }
            case "list_settings": return listSettings();
            case "get_setting": return getSetting(args.getString("key"));
            case "set_setting": return setSetting(args.getString("key"), args.optString("value", ""));
            case "key_pool_list": return keyPoolList(args);
            case "key_pool_add": return keyPoolAdd(args);
            case "key_pool_remove": return keyPoolRemove(args);
            case "key_pool_update": return keyPoolUpdate(args);
            case "web_search": return webSearch(args);
            case "browser_open": return browserOpen(args);
            case "browser_status": return browserStatus();
            case "browser_extract": return browserExtract(args);
            case "browser_click": return browserClick(args);
            case "browser_type": return browserType(args);
            case "browser_scroll": return browserScroll(args);
            case "browser_back": return browserBack();
            case "browser_eval": return browserEval(args);
            case "browser_screenshot": return browserScreenshot(args);
            case "browser_ua": return browserUa(args);
            case "web_vision": return webVision(args);
            case "image_generate": return imageGenerate(args);
            case "rename_conv": return renameConv(args);
            default: throw new Exception("未知工具: " + name);
        }
    }


    private static String ttsSpeak(String text) {
        if (text == null || text.trim().isEmpty()) return "无内容可朗读";
        TtsEngine.get(App.inst).speak(text.trim());
        return "正在朗读：" + (text.trim().length() > 60 ? text.trim().substring(0, 60) + "…" : text.trim());
    }

    // ─── 设置管理（AI 自行配置入口） ───

    private static org.json.JSONArray keyPoolArray() throws Exception {
        String s = Prefs.get(App.inst).apiKeyPool();
        if (s == null || s.trim().isEmpty()) s = "[]";
        return new org.json.JSONArray(s);
    }

    private static void keyPoolSave(org.json.JSONArray arr) throws Exception {
        Prefs.get(App.inst).apiKeyPool(arr.toString());
    }

    private static String keyPoolList(JSONObject a) {
        try {
            JSONArray arr = keyPoolArray();
            if (arr.length() == 0)
                return "密钥池为空（0 个服务商）。可用 key_pool_add 添加（name/url/key 必填）。";
            StringBuilder sb = new StringBuilder("密钥池（共 " + arr.length() + " 个服务商，key 已脱敏）：\n");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                sb.append(i + 1).append(". ").append(o.optString("name", "(未命名)"))
                        .append(" | id=").append(o.optString("id", ""))
                        .append(" | key=").append(o.optString("key", "").isEmpty() ? "未设置" : maskKey(o.optString("key", "")))
                        .append("\n   url=").append(o.optString("url", ""))
                        .append("\n   models=").append(o.optJSONArray("models") == null ? "" : o.optJSONArray("models").toString())
                        .append("\n");
            }
            return sb.toString();
        } catch (Exception e) { return "[密钥池读取失败] " + e.getMessage(); }
    }

    private static String keyPoolAdd(JSONObject a) {
        try {
            String name = a.optString("name", "").trim();
            String url = a.optString("url", "").trim();
            String key = a.optString("key", "").trim();
            if (name.isEmpty()) return "错误：name 不能为空（给服务商起个名字，如 魔塔/硅基流动）";
            if (url.isEmpty()) return "错误：url 不能为空（OpenAI 兼容接口地址）";
            if (key.isEmpty()) return "错误：key 不能为空（API 密钥）";
            JSONArray arr = keyPoolArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (name.equals(o.optString("name", "")))
                    return "错误：已存在名为「" + name + "」的服务商，如需修改请用 key_pool_update";
            }
            JSONObject ne = new JSONObject();
            ne.put("id", String.valueOf(System.currentTimeMillis()));
            ne.put("name", name);
            ne.put("url", url);
            ne.put("key", key);
            JSONArray models = new JSONArray();
            String ms = a.optString("models", "").trim();
            if (!ms.isEmpty()) for (String m : ms.split("[,，]")) if (!m.trim().isEmpty()) models.put(m.trim());
            ne.put("models", models);
            arr.put(ne);
            int idx = arr.length() - 1;
            keyPoolSave(arr);
            Ui.H.post(() -> {
                MainActivity ma = MainActivity.instance();
                if (ma != null && ma.chatPage() != null) ma.chatPage().loadModels();
            });
            return "已添加密钥「" + name + "」（当前共 " + arr.length() + " 个服务商，index=" + idx + "）。"
                    + "模型列表已刷新。如需立即启用，可用 set_setting 设置 activeKeyIndex=" + idx
                    + "（或 activeCloudModel=某个模型名）；也可在设置→模型列表中直接选用。";
        } catch (Exception e) { return "[添加失败] " + e.getMessage(); }
    }

    private static String keyPoolRemove(JSONObject a) {
        try {
            String name = a.optString("name", "").trim();
            String id = a.optString("id", "").trim();
            if (name.isEmpty() && id.isEmpty()) return "错误：请提供 name 或 id 指定要删除的服务商";
            JSONArray arr = keyPoolArray();
            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject o = arr.getJSONObject(i);
                boolean match = (!name.isEmpty() && name.equals(o.optString("name", "")))
                        || (!id.isEmpty() && id.equals(o.optString("id", "")));
                if (match) {
                    String removed = o.optString("name", "(未命名)");
                    arr.remove(i);
                    keyPoolSave(arr);
                    Ui.H.post(() -> {
                        MainActivity ma = MainActivity.instance();
                        if (ma != null && ma.chatPage() != null) ma.chatPage().loadModels();
                    });
                    return "已删除密钥「" + removed + "」，剩余 " + arr.length() + " 个服务商，模型列表已刷新";
                }
            }
            return "未找到匹配的服务商（name=" + (name.isEmpty() ? "-" : name) + ", id=" + (id.isEmpty() ? "-" : id) + "）。可用 key_pool_list 查看现有列表";
        } catch (Exception e) { return "[删除失败] " + e.getMessage(); }
    }

    private static String keyPoolUpdate(JSONObject a) {
        try {
            String name = a.optString("name", "").trim();
            String id = a.optString("id", "").trim();
            if (name.isEmpty() && id.isEmpty()) return "错误：请提供 name 或 id 指定要修改的服务商";
            JSONArray arr = keyPoolArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                boolean match = (!name.isEmpty() && name.equals(o.optString("name", "")))
                        || (!id.isEmpty() && id.equals(o.optString("id", "")));
                if (match) {
                    String oldName = o.optString("name", "(未命名)");
                    if (a.has("key")) o.put("key", a.optString("key", "").trim());
                    if (a.has("url")) o.put("url", a.optString("url", "").trim());
                    if (a.has("newName")) o.put("name", a.optString("newName", "").trim());
                    if (a.has("models")) {
                        JSONArray models = new JSONArray();
                        String ms = a.optString("models", "").trim();
                        if (!ms.isEmpty()) for (String m : ms.split("[,，]")) if (!m.trim().isEmpty()) models.put(m.trim());
                        o.put("models", models);
                    }
                    keyPoolSave(arr);
                    Ui.H.post(() -> {
                        MainActivity ma = MainActivity.instance();
                        if (ma != null && ma.chatPage() != null) ma.chatPage().loadModels();
                    });
                    return "已更新密钥「" + oldName + "」，模型列表已刷新";
                }
            }
            return "未找到匹配的服务商（name=" + (name.isEmpty() ? "-" : name) + ", id=" + (id.isEmpty() ? "-" : id) + "）。可用 key_pool_list 查看现有列表";
        } catch (Exception e) { return "[更新失败] " + e.getMessage(); }
    }

    /** 联网搜索：优先必应（真实 URL、中文友好），回退 DuckDuckGo HTML */
    private static String webSearch(JSONObject a) throws Exception {
        String query = a.optString("query", "").trim();
        if (query.isEmpty()) throw new Exception("query 不能为空（搜索关键词，尽量具体）");
        int max = Math.min(Math.max(a.optInt("max", 8), 1), 10);
        java.util.Map<String, String> hdr = new java.util.HashMap<>();
        hdr.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        hdr.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        String q = java.net.URLEncoder.encode(query, "UTF-8");
        // 主源：360 搜索（中文结果真实可靠，反爬宽松，桌面 UA 即可）
        Http.Resp r = Http.get("https://www.so.com/s?q=" + q, hdr, 15000);
        if (r.code == 200 && r.body != null && r.body.contains("res-title")) {
            String parsed = parse360(r.body, query, max);
            if (!parsed.startsWith("未解析到")) return parsed;
        }
        // 备源：必应（国际/英文查询）
        Http.Resp r2 = Http.get("https://www.bing.com/search?q=" + q + "&mkt=zh-CN", hdr, 15000);
        if (r2.code == 200 && r2.body != null && r2.body.contains("b_algo")) {
            String parsed = parseBing(r2.body, query, max);
            if (!parsed.startsWith("未解析到")) return parsed;
        }
        String err = r.body == null ? "" : " 百度片段：" + clip(r.body);
        return "[搜索失败] 百度 HTTP " + r.code + " / 必应 HTTP " + r2.code + err;
    }

    /** 解析 360 搜索 HTML：h3.res-title 标题 + data-mdurl 真实链接 + p.res-desc 摘要（indexOf 版，避正则转义） */
    private static String parse360(String html, String query, int max) {
        StringBuilder sb = new StringBuilder("\uD83D\uDD0D 搜索结果（360）：「" + query + "」\n");
        int idx = 0;
        int n = 0;
        while (n < max) {
            idx = html.indexOf("res-title", idx);
            if (idx < 0) break;
            int h3start = html.lastIndexOf("<h3", idx);
            int h3end = html.indexOf("</h3>", idx);
            if (h3start < 0 || h3end < 0) break;
            String block = html.substring(h3start, h3end);
            String title = stripHtml(block).trim();
            String link = "";
            int mi = block.indexOf("data-mdurl=");
            if (mi >= 0) {
                int qi = mi + 11;
                if (qi < block.length() && block.charAt(qi) == '\"') {
                    int qe = block.indexOf('\"', qi + 1);
                    if (qe > qi) link = block.substring(qi + 1, qe);
                }
            }
            String snip = "";
            int di = html.indexOf("res-desc", h3end);
            if (di >= 0 && di - h3end < 3000) {
                int ds = html.indexOf('>', di);
                int de = html.indexOf("</p>", di);
                if (ds >= 0 && de > ds) snip = stripHtml(html.substring(ds + 1, de)).trim();
            }
            idx = h3end;
            if (title.isEmpty() || title.length() < 3) continue;
            if (title.contains("其他人还搜") || title.contains("相关搜索") || title.contains("猜您关注")
                    || title.contains("相关书籍") || title.contains("反馈")) continue;
            sb.append(n + 1).append(". ").append(title).append("\n");
            if (!link.isEmpty()) sb.append("   ").append(link).append("\n");
            if (!snip.isEmpty()) sb.append("   ").append(snip).append("\n");
            n++;
        }
        return n > 0 ? sb.toString().trim() : "未解析到结果";
    }

    /** 解析百度搜索 HTML：h3 标题 + 超长 href 链接（m.baidu.com 跳转）+ c-abstract/c-line-clamp 摘要 */
    private static String parseBaidu(String html, String query, int max) {
        StringBuilder sb = new StringBuilder("🔍 搜索结果（百度）：「" + query + "」\n");
        java.util.regex.Pattern h3p = java.util.regex.Pattern.compile("<h3[^>]*>(.*?)</h3>", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher m = h3p.matcher(html);
        java.util.ArrayList<int[]> spans = new java.util.ArrayList<>();
        java.util.ArrayList<String> ts = new java.util.ArrayList<>();
        while (m.find()) {
            String t = stripHtml(m.group(1));
            if (t.length() < 3) continue;
            spans.add(new int[]{m.start(), m.end()});
            ts.add(t);
        }
        int n = 0;
        for (int i = 0; i < spans.size() && n < max; i++) {
            String title = ts.get(i);
            // 链接：从 h3 往回最多 6000 字符，取最后一个 http(s)// href（百度 href 值超长）
            String pre = html.substring(Math.max(0, spans.get(i)[0] - 6000), spans.get(i)[0]);
            java.util.regex.Matcher lm = java.util.regex.Pattern.compile(
                    "href=\"([^\"]{5,})\"", java.util.regex.Pattern.DOTALL).matcher(pre);
            String link = "";
            while (lm.find()) {
                String v = lm.group(1);
                if (v.startsWith("http://") || v.startsWith("https://") || v.startsWith("//")) link = v;
            }
            if (link.startsWith("//")) link = "https:" + link;
            // 摘要：h3 结束到下一个 h3 之间
            int end = (i + 1 < spans.size()) ? spans.get(i + 1)[0] : Math.min(spans.get(i)[1] + 2500, html.length());
            String seg = html.substring(spans.get(i)[1], end);
            String snip = "";
            java.util.regex.Matcher am = java.util.regex.Pattern.compile(
                    "c-abstract[^>]*>(.*?)</div>", java.util.regex.Pattern.DOTALL).matcher(seg);
            if (am.find()) snip = stripHtml(am.group(1));
            if (snip.isEmpty()) {
                java.util.regex.Matcher cm = java.util.regex.Pattern.compile(
                        "c-line-clamp[^>]*>(.*?)<", java.util.regex.Pattern.DOTALL).matcher(seg);
                if (cm.find()) snip = stripHtml(cm.group(1));
            }
            if (snip.isEmpty()) {
                String st = stripHtml(seg);
                snip = st.length() > 160 ? st.substring(0, 160) : st;
            }
            // 过滤百度 AI 摘要 JSON 块
            if (snip.startsWith("{\"") || snip.contains(",\"isSingleLine\"") || snip.contains(",\"isZyC\"")) continue;
            if (snip.length() > 220) snip = snip.substring(0, 220) + "…";
            sb.append((n + 1) + ". " + title + "\n   " + link + "\n   " + snip + "\n");
            n++;
        }
        if (n == 0) return "未解析到结果（百度页面结构可能变化）";
        return sb.toString();
    }

    private static String parseBing(String html, String query, int max) {
        StringBuilder sb = new StringBuilder("🔍 搜索结果（必应）：「" + query + "」\n");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "<li class=\"b_algo\"[^>]*>(.*?)</li>", java.util.regex.Pattern.DOTALL).matcher(html);
        int n = 0;
        while (m.find() && n < max) {
            String block = m.group(1);
            // 链接：块内第一个 https 外链（必应结果 h2 被 <a> 包裹，独立提取最稳）
            java.util.regex.Matcher lm = java.util.regex.Pattern.compile(
                    "href=\"(https?://[^\"]+)\"", java.util.regex.Pattern.DOTALL).matcher(block);
            String link = lm.find() ? lm.group(1) : "";
            // 标题：h2 标签内文本
            java.util.regex.Matcher tm = java.util.regex.Pattern.compile(
                    "<h2[^>]*>(.*?)</h2>", java.util.regex.Pattern.DOTALL).matcher(block);
            String title = tm.find() ? stripHtml(tm.group(1)) : "";
            if (title.isEmpty()) continue;
            // 摘要：p 标签内文本
            java.util.regex.Matcher pm = java.util.regex.Pattern.compile(
                    "<p[^>]*>(.*?)</p>", java.util.regex.Pattern.DOTALL).matcher(block);
            String snip = pm.find() ? stripHtml(pm.group(1)) : "";
            if (snip.length() > 220) snip = snip.substring(0, 220) + "…";
            sb.append((n + 1) + ". " + title + "\n   " + link + "\n   " + snip + "\n");
            n++;
        }
        if (n == 0) return "未解析到结果（必应可能改版或限流，稍后重试或换关键词）";
        return sb.toString();
    }


    /** 去 HTML 标签与常见实体，返回纯文本 */
    private static String stripHtml(String s) {
        if (s == null) return "";
        s = s.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
             .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
             .replaceAll("(?is)<[^>]+>", " ")
             .replace("&nbsp;", " ").replace("&amp;", "&")
             .replace("&lt;", "<").replace("&gt;", ">")
             .replace("&quot;", "\"").replace("&#39;", "'")
             .replace("&#x27;", "'").replace("&#x2F;", "/")
             .replaceAll("[ \\t\\x0B\\f]+", " ").trim();
        return s;
    }

    private static String listSettings() {
        Prefs p = Prefs.get(App.inst);
        StringBuilder sb = new StringBuilder("应用设置清单（键 = 当前值）：\n");
        sb.append("host = ").append(p.host()).append("\n");
        sb.append("hosts = ").append(joinHosts(p.hosts())).append("（历史服务地址）\n");
        sb.append("port = ").append(p.port()).append("\n");
        sb.append("timeoutSec = ").append(p.timeoutSec()).append("\n");
        sb.append("retryMax = ").append(p.retryMax()).append("\n");
        sb.append("editMode = ").append(p.editMode()).append("（工具执行模式）\n");
        sb.append("themeName = ").append(p.themeName()).append("\n");
        sb.append("customTheme = ").append(p.customTheme()).append("\n");
        sb.append("cBg = ").append(hexOf(p.cBg())).append("（自定义背景色）\n");
        sb.append("cAccent = ").append(hexOf(p.cAccent())).append("（自定义强调色）\n");
        sb.append("cText = ").append(hexOf(p.cText())).append("（自定义文字色）\n");
        sb.append("fontScale = ").append(p.fontScale()).append("\n");
        sb.append("stream = ").append(p.stream()).append("（流式输出）\n");
        sb.append("showThink = ").append(p.showThink()).append("（显示思考链）\n");
        sb.append("streamDiag = ").append(p.streamDiag()).append("\n");
        sb.append("temperature = ").append(p.temperature()).append("\n");
        sb.append("topP = ").append(p.topP()).append("\n");
        sb.append("maxTokens = ").append(p.maxTokens()).append("\n");
        sb.append("ctxMsgs = ").append(p.ctxMsgs()).append("\n");
        sb.append("summaryKb = ").append(p.summaryKb()).append("\n");
        sb.append("sysPrompt = ").append(p.sysPrompt().isEmpty() ? "(空)" : p.sysPrompt()).append("\n");
        sb.append("cloudMode = ").append(p.cloudMode()).append("（云端模式）\n");
        sb.append("cloudUrl = ").append(p.cloudUrl()).append("\n");
        sb.append("cloudKey = ").append(p.cloudKey().isEmpty() ? "(未设置)" : "••••已设置").append("\n");
        sb.append("cloudModels = ").append(p.cloudModels()).append("\n");
        sb.append("activeModel = ").append(p.activeModel().isEmpty() ? "(自动)" : p.activeModel()).append("\n");
        sb.append("activeCloudModel = ").append(p.activeCloudModel().isEmpty() ? "(自动)" : p.activeCloudModel()).append("\n");
        sb.append("ttsMode = ").append(p.ttsMode()).append("（system/http）\n");
        sb.append("ttsUrl = ").append(p.ttsUrl()).append("\n");
        sb.append("ttsKey = ").append(p.ttsKey().isEmpty() ? "(未设置)" : "••••已设置").append("\n");
        sb.append("ttsModel = ").append(p.ttsModel()).append("\n");
        sb.append("ttsVoice = ").append(p.ttsVoice()).append("\n");
        sb.append("ttsSpeed = ").append(p.ttsSpeed()).append("\n");
        sb.append("autoTts = ").append(p.autoTts()).append("（自动朗读 AI 回复）\n");
        sb.append("activeKeyIndex = ").append(p.activeKeyIndex()).append("\n");
        sb.append("apiKeyPool = ").append(maskApiKeyPool(p.apiKeyPool())).append("\n");
        sb.append("workspace = ").append(p.workspace()).append("\n");
        sb.append("imgEnabled = ").append(p.imgEnabled()).append("（生图 AI 开关）\n");
        sb.append("imgUrl = ").append(p.imgUrl()).append("\n");
        sb.append("imgKey = ").append(p.imgKey().isEmpty() ? "(未设置)" : "••••已设置").append("\n");
        sb.append("imgModel = ").append(p.imgModel()).append("\n");
        sb.append("imgSize = ").append(p.imgSize()).append("\n");
        sb.append("imgStyle = ").append(p.imgStyle().isEmpty() ? "(空)" : p.imgStyle()).append("\n");
        sb.append("imgDir = ").append(p.imgDir()).append("（生图输出目录，相对工作区）\n");
        sb.append("visionMode = ").append(p.visionMode()).append("（视觉模式：follow=沿用主模型 / manual=手动独立配置）\n");
        sb.append("visionUrl = ").append(p.visionUrl().isEmpty() ? "(空)" : p.visionUrl()).append("（手动模式视觉接口地址）\n");
        sb.append("visionKey = ").append(p.visionKey().isEmpty() ? "(未设置)" : "••••已设置").append("\n");
        sb.append("visionModelId = ").append(p.visionModelId().isEmpty() ? "(空)" : p.visionModelId()).append("（手动模式视觉模型 ID）\n");
        sb.append("imgVisionModel = ").append(p.imgVisionModel().isEmpty() ? "(自动)" : p.imgVisionModel()).append("（视觉模型，空则用 activeModel/activeCloudModel）\n");
        sb.append("autoTitle = ").append(p.autoTitle()).append("（AI 自主会话命名）\n");
        return sb.toString();
    }

    private static String getSetting(String key) throws Exception {
        if (key == null || key.trim().isEmpty()) throw new Exception("key 不能为空，可用 list_settings 查看所有键");
        String k = key.trim();
        Prefs p = Prefs.get(App.inst);
        switch (k) {
            case "host": return "host = " + p.host();
            case "hosts": return "hosts = " + joinHosts(p.hosts());
            case "port": return "port = " + p.port();
            case "timeoutSec": return "timeoutSec = " + p.timeoutSec();
            case "retryMax": return "retryMax = " + p.retryMax();
            case "editMode": return "editMode = " + p.editMode();
            case "themeName": return "themeName = " + p.themeName();
            case "customTheme": return "customTheme = " + p.customTheme();
            case "cBg": return "cBg = " + hexOf(p.cBg());
            case "cAccent": return "cAccent = " + hexOf(p.cAccent());
            case "cText": return "cText = " + hexOf(p.cText());
            case "fontScale": return "fontScale = " + p.fontScale();
            case "stream": return "stream = " + p.stream();
            case "showThink": return "showThink = " + p.showThink();
            case "streamDiag": return "streamDiag = " + p.streamDiag();
            case "temperature": return "temperature = " + p.temperature();
            case "topP": return "topP = " + p.topP();
            case "maxTokens": return "maxTokens = " + p.maxTokens();
            case "ctxMsgs": return "ctxMsgs = " + p.ctxMsgs();
            case "summaryKb": return "summaryKb = " + p.summaryKb();
            case "sysPrompt": return "sysPrompt = " + (p.sysPrompt().isEmpty() ? "(空)" : p.sysPrompt());
            case "cloudMode": return "cloudMode = " + p.cloudMode();
            case "cloudUrl": return "cloudUrl = " + p.cloudUrl();
            case "cloudKey": return "cloudKey = " + (p.cloudKey().isEmpty() ? "(未设置)" : "••••已设置");
            case "cloudModels": return "cloudModels = " + p.cloudModels();
            case "activeModel": return "activeModel = " + (p.activeModel().isEmpty() ? "(自动)" : p.activeModel());
            case "activeCloudModel": return "activeCloudModel = " + (p.activeCloudModel().isEmpty() ? "(自动)" : p.activeCloudModel());
            case "ttsMode": return "ttsMode = " + p.ttsMode();
            case "ttsUrl": return "ttsUrl = " + p.ttsUrl();
            case "ttsKey": return "ttsKey = " + (p.ttsKey().isEmpty() ? "(未设置)" : "••••已设置");
            case "ttsModel": return "ttsModel = " + p.ttsModel();
            case "ttsVoice": return "ttsVoice = " + p.ttsVoice();
            case "ttsSpeed": return "ttsSpeed = " + p.ttsSpeed();
            case "autoTts": return "autoTts = " + p.autoTts();
            case "activeKeyIndex": return "activeKeyIndex = " + p.activeKeyIndex();
            case "apiKeyPool": return "apiKeyPool = " + maskApiKeyPool(p.apiKeyPool());
            case "workspace": return "workspace = " + p.workspace();
            case "imgEnabled": return "imgEnabled = " + p.imgEnabled();
            case "imgUrl": return "imgUrl = " + p.imgUrl();
            case "imgKey": return "imgKey = " + (p.imgKey().isEmpty() ? "(未设置)" : "••••已设置");
            case "imgModel": return "imgModel = " + p.imgModel();
            case "imgSize": return "imgSize = " + p.imgSize();
            case "imgStyle": return "imgStyle = " + (p.imgStyle().isEmpty() ? "(空)" : p.imgStyle());
            case "imgDir": return "imgDir = " + p.imgDir();
            case "visionMode": return "visionMode = " + p.visionMode() + "（follow/manual）";
            case "visionUrl": return "visionUrl = " + (p.visionUrl().isEmpty() ? "(空)" : p.visionUrl());
            case "visionKey": return "visionKey = " + (p.visionKey().isEmpty() ? "(未设置)" : "••••已设置");
            case "visionModelId": return "visionModelId = " + (p.visionModelId().isEmpty() ? "(空)" : p.visionModelId());
            case "imgVisionModel": return "imgVisionModel = " + (p.imgVisionModel().isEmpty() ? "(自动)" : p.imgVisionModel());
            case "autoTitle": return "autoTitle = " + p.autoTitle();
            default: throw new Exception("未知设置键: " + k + "（可用 list_settings 查看全部）");
        }
    }

    private static String setSetting(String key, String value) throws Exception {
        if (key == null || key.trim().isEmpty()) throw new Exception("key 不能为空");
        String k = key.trim();
        String v = value == null ? "" : value.trim();
        Prefs p = Prefs.get(App.inst);
        switch (k) {
            case "host": p.host(v); break;
            case "hosts": {
                java.util.ArrayList<String> list = new java.util.ArrayList<>();
                for (String h : v.split("[,，]")) if (!h.trim().isEmpty()) list.add(h.trim());
                if (list.isEmpty()) throw new Exception("hosts 需要至少一个地址（逗号分隔）");
                p.hosts(list);
                break;
            }
            case "port": p.port(parseInt(v, "port")); break;
            case "timeoutSec": p.timeoutSec(parseInt(v, "timeoutSec")); break;
            case "retryMax": p.retryMax(parseInt(v, "retryMax")); break;
            case "editMode": p.editMode(parseBool(v, "editMode")); break;
            case "themeName": p.themeName(v); break;
            case "customTheme": p.customTheme(parseBool(v, "customTheme")); break;
            case "cBg": p.colors(parseColor(v, "cBg"), p.cAccent(), p.cText()); break;
            case "cAccent": p.colors(p.cBg(), parseColor(v, "cAccent"), p.cText()); break;
            case "cText": p.colors(p.cBg(), p.cAccent(), parseColor(v, "cText")); break;
            case "fontScale": p.fontScale(parseFloat(v, "fontScale")); break;
            case "stream": p.stream(parseBool(v, "stream")); break;
            case "showThink": p.showThink(parseBool(v, "showThink")); break;
            case "streamDiag": p.streamDiag(parseBool(v, "streamDiag")); break;
            case "temperature": p.temperature(parseFloat(v, "temperature")); break;
            case "topP": p.topP(parseFloat(v, "topP")); break;
            case "maxTokens": p.maxTokens(parseInt(v, "maxTokens")); break;
            case "ctxMsgs": p.ctxMsgs(parseInt(v, "ctxMsgs")); break;
            case "summaryKb": p.summaryKb(parseInt(v, "summaryKb")); break;
            case "sysPrompt": p.sysPrompt(v); break;
            case "cloudMode": p.cloudMode(parseBool(v, "cloudMode")); break;
            case "cloudUrl": p.cloudUrl(v); break;
            case "cloudKey": p.cloudKey(v); break;
            case "cloudModels": p.cloudModels(v); break;
            case "activeModel": p.activeModel(v); break;
            case "activeCloudModel": p.activeCloudModel(v); break;
            case "ttsMode":
                if (!"system".equals(v) && !"http".equals(v) && !"edge".equals(v))
                    throw new Exception("ttsMode 只能为 system / http / edge");
                p.ttsMode(v); break;
            case "ttsUrl": p.ttsUrl(v); break;
            case "ttsKey": p.ttsKey(v); break;
            case "ttsModel": p.ttsModel(v); break;
            case "ttsVoice": p.ttsVoice(v); break;
            case "ttsSpeed": p.ttsSpeed(parseFloat(v, "ttsSpeed")); break;
            case "autoTts": p.autoTts(parseBool(v, "autoTts")); break;
            case "activeKeyIndex": p.activeKeyIndex(parseInt(v, "activeKeyIndex")); break;
            case "apiKeyPool": p.apiKeyPool(v); break;
            case "workspace": p.workspace(v); break;
            case "imgEnabled": p.imgEnabled(parseBool(v, "imgEnabled")); break;
            case "imgUrl": p.imgUrl(v); break;
            case "imgKey": p.imgKey(v); break;
            case "imgModel": p.imgModel(v); break;
            case "imgSize": p.imgSize(v); break;
            case "imgStyle": p.imgStyle(v); break;
            case "imgDir": p.imgDir(v); break;
            case "imgVisionModel": p.imgVisionModel(v); break;
            case "visionMode":
                if (!"follow".equals(v) && !"manual".equals(v))
                    throw new Exception("visionMode 只能为 follow / manual");
                p.visionMode(v); break;
            case "visionUrl": p.visionUrl(v); break;
            case "visionKey": p.visionKey(v); break;
            case "visionModelId": p.visionModelId(v); break;
            case "autoTitle": p.autoTitle(parseBool(v, "autoTitle")); break;
            default: throw new Exception("未知设置键: " + k + "（可用 list_settings 查看全部）");
        }
        Ui.H.post(() -> {
            MainActivity a = MainActivity.instance();
            if (a != null && a.chatPage() != null) a.chatPage().refreshChips();
        });
        return "已设置 " + k + " = " + v;
    }

    private static int parseInt(String v, String k) throws Exception {
        try { return Integer.parseInt(v.trim()); }
        catch (Exception e) { throw new Exception(k + " 需要整数，收到: " + v); }
    }

    private static float parseFloat(String v, String k) throws Exception {
        try { return Float.parseFloat(v.trim()); }
        catch (Exception e) { throw new Exception(k + " 需要数字，收到: " + v); }
    }

    private static boolean parseBool(String v, String k) throws Exception {
        String s = v.trim().toLowerCase();
        if ("true".equals(s) || "1".equals(s) || "yes".equals(s) || "on".equals(s)) return true;
        if ("false".equals(s) || "0".equals(s) || "no".equals(s) || "off".equals(s)) return false;
        throw new Exception(k + " 需要 true/false，收到: " + v);
    }

    /** 历史 host 列表 → 逗号分隔字符串 */
    private static String joinHosts(java.util.ArrayList<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    /** int 颜色 → #RRGGBB（供 list_settings / get_setting 可读输出） */
    private static String hexOf(int c) {
        return String.format("#%02X%02X%02X", (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF);
    }

    /** 解析 #RRGGBB 或 0xRRGGBB 颜色，失败报错 */
    private static int parseColor(String v, String k) throws Exception {
        String t = v.trim();
        if (t.startsWith("#")) t = t.substring(1);
        else if (t.startsWith("0x") || t.startsWith("0X")) t = t.substring(2);
        try {
            int rgb = Integer.parseInt(t, 16);
            return 0xFF000000 | rgb;
        } catch (Exception e) {
            throw new Exception(k + " 需要颜色值（如 #E0DECF），收到: " + v);
        }
    }

    /** API key 完全打码：只返回占位符，绝不向 AI 暴露任何 key 字符（防止泄露进云端模型上下文） */
    private static String maskKey(String k) {
        return "••••（已隐藏）";
    }

    /** apiKeyPool 脱敏：只打码各服务商的 key 字段，保留 name/url/models 供 AI 了解可用服务 */
    private static String maskApiKeyPool(String json) {
        if (json == null || json.trim().isEmpty() || "[]".equals(json.trim())) return "[]";
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                String k = o.optString("key", "");
                if (!k.isEmpty()) o.put("key", maskKey(k));
            }
            return arr.toString();
        } catch (Exception e) {
            return "[apiKeyPool 格式异常，未展开]";
        }
    }
    /** 所有会话共享主工作区（已移除按会话 id 隔离的 convs/<会话id> 机制）。 */
    private static File convWorkspace() {
        try {
            File main = new File(Prefs.get(App.inst).workspace());
            if (!main.exists()) main.mkdirs();
            return main;
        } catch (Exception e) {
            return new File(Prefs.get(App.inst).workspace());
        }
    }

    private static File resolve(String p) throws Exception {
        Prefs pref = Prefs.get(App.inst);
        File ws = convWorkspace();
        File f;
        if (p == null || p.trim().isEmpty()) f = ws;
        else {
            f = new File(p.trim());
            if (!f.isAbsolute()) f = new File(ws, p.trim());
        }
        String canon = f.getCanonicalPath();
        boolean manager = android.os.Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager();
        if (!manager) {
            String wsCanon = ws.getCanonicalPath();
            if (!canon.equals(wsCanon) && !canon.startsWith(wsCanon + File.separator)) {
                throw new Exception("路径超出工作区范围：" + p + "（可在设置中授予所有文件权限后操作任意路径）");
            }
        }
        return new File(canon);
    }

    private static String listFiles(File dir) throws Exception {
        if (!dir.exists()) throw new Exception("目录不存在: " + dir.getName());
        if (!dir.isDirectory()) throw new Exception("不是目录: " + dir.getName());
        File[] fs = dir.listFiles();
        if (fs == null || fs.length == 0) return "(空目录)";
        StringBuilder sb = new StringBuilder("[D] 表示目录\n");
        for (File f : fs) {
            sb.append(f.isDirectory() ? "[D] " : "[F] ").append(f.getName());
            if (f.isFile()) sb.append("  (").append(f.length()).append(" B)");
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String readFile(File f) throws Exception {
        if (!f.exists()) throw new Exception("文件不存在: " + f.getName());
        if (f.length() > 2 * 1024 * 1024) throw new Exception("文件过大(>2MB)");
        String s = ConvStore.readQuietly(f, 32000);
        if (s.length() >= 32000) s = s + "\n…[已截断]";
        return s.isEmpty() ? "(空文件)" : s;
    }

    private static String writeFile(String path, String content, boolean append) throws Exception {
        File f = resolve(path);
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        java.io.FileOutputStream fo = new java.io.FileOutputStream(f, append);
        fo.write(content.getBytes(StandardCharsets.UTF_8));
        if (append && !content.endsWith("\n") && !content.isEmpty()) fo.write('\n');
        fo.close();
        return (append ? "已追加" : "已写入") + ": " + f.getAbsolutePath()
                + " (" + f.length() + " B)";
    }

    private static String deletePath(File f) throws Exception {
        if (!f.exists()) throw new Exception("不存在: " + f.getName());
        rmRf(f);
        return "已删除: " + f.getAbsolutePath();
    }

    private static void rmRf(File f) {
        if (f.isDirectory()) {
            File[] fs = f.listFiles();
            if (fs != null) for (File c : fs) rmRf(c);
        }
        f.delete();
    }

    private static String makeDir(File d) throws Exception {
        boolean ok = d.isDirectory() || d.mkdirs();
        if (!ok && !d.isDirectory()) throw new Exception("创建失败: " + d.getName());
        return "已创建目录: " + d.getAbsolutePath();
    }

    private static String runCommand(String command) throws Exception {
        File ws = convWorkspace();
        ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-c", command);
        pb.directory(ws);
        pb.redirectErrorStream(true);
        java.util.Map<String, String> env = pb.environment();
        env.put("HOME", App.inst.getFilesDir().getAbsolutePath());
        env.put("TMPDIR", App.inst.getCacheDir().getAbsolutePath());
        env.put("PATH", "/system/bin:/system/xbin:/vendor/bin");
        env.put("LANG", "en_US.UTF-8");
        env.put("PWD", ws.getAbsolutePath());
        Process proc = pb.start();
        StringBuilder out = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));
        Thread reader = new Thread(() -> {
            try {
                char[] buf = new char[2048];
                int n;
                while ((n = br.read(buf)) > 0) {
                    if (out.length() < 16000) out.append(buf, 0, n);
                }
            } catch (Exception ignored) {}
        });
        reader.start();
        boolean finished = proc.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            proc.destroyForcibly();
            return "[超时] 命令执行超过 60 秒被终止\n" + clip(out.toString());
        }
        reader.join(2000);
        int code = proc.exitValue();
        String body = out.toString().trim();
        if (body.isEmpty()) body = "(无输出)";
        return "$ " + command + "\n[exit " + code + "]\n" + clip(body);
    }

    private static String clip(String s) {
        if (s == null) return "";
        return s.length() > 16000 ? s.substring(0, 16000) + "\n…[截断]" : s;
    }

    private static String webFetch(String url) throws Exception {
        if (url == null || !url.startsWith("http")) throw new Exception("URL 必须以 http 开头");
        Http.Resp r = Http.get(url, null, 15000);
        if (r.code != 200) return "[HTTP " + r.code + "] " + clip(r.body);
        String ct = "";
        String text = r.body == null ? "" : r.body;
        String lower = text.substring(0, Math.min(2000, text.length())).toLowerCase();
        if (lower.contains("<html") || lower.contains("<!doctype")) {
            ct = "text/html";
            text = text.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                       .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                       .replaceAll("(?is)<[^>]+>", " ")
                       .replace("&nbsp;", " ").replace("&amp;", "&")
                       .replace("&lt;", "<").replace("&gt;", ">")
                       .replace("&quot;", "\"").replace("&#39;", "'")
                       .replaceAll("[ \\t\\x0B\\f]+", " ")
                       .replaceAll("\\n\\s*\\n+", "\n").trim();
        } else {
            try { ct = "application/json"; new JSONObject(text); } catch (Exception e) { ct = "text/plain"; }
        }
        if (text.length() > 20000) text = text.substring(0, 20000) + "\n…[已截断]";
        return "[" + ct + " " + url + "]\n" + (text.isEmpty() ? "(空)" : text);
    }

    private static String webOpen(String url) throws Exception {
        MainActivity act = MainActivity.instance();
        if (act == null) throw new Exception("应用不在前台");
        if (url == null || !url.startsWith("http")) throw new Exception("URL 必须以 http 开头");
        Ui.H.post(() -> {
            act.switchTo("web");
            WebPage wp = act.webPage();
            if (wp != null) wp.navigate(url);
        });
        return "已在内置浏览器打开: " + url;
    }

    private static String createSkill(JSONObject args) throws Exception {
        String name = args.getString("name").trim();
        if (name.isEmpty()) throw new Exception("name 不能为空");
        String instructions = args.optString("instructions", "");
        if (instructions.trim().isEmpty()) throw new Exception("instructions 不能为空（技能的具体指令内容）");
        if (instructions.length() > 6000) throw new Exception("instructions 过长（>6000 字符），请精简");
        java.util.List<Skills.S> list = Skills.list(App.inst);
        Skills.S target = null;
        for (Skills.S s : list) if (name.equals(s.name)) { target = s; break; }
        boolean update = target != null;
        if (target == null) { target = Skills.blank(); target.name = name; list.add(target); }
        target.desc = args.optString("desc", "");
        target.instructions = instructions;
        target.enabled = args.optBoolean("enabled", true);
        Skills.saveAll(App.inst, list);
        Ui.H.post(() -> {
            MainActivity a = MainActivity.instance();
            if (a != null && a.workPage() != null) a.workPage().refreshData();
        });
        return (update ? "已更新" : "已创建") + " Skill「" + name + "」"
                + (target.enabled ? "（已启用，下次对话生效）" : "（未启用，可在 工作台→Skill 中开启）");
    }

    private static String deleteSkill(JSONObject args) throws Exception {
        String name = args.getString("name").trim();
        java.util.List<Skills.S> list = Skills.list(App.inst);
        for (int i = 0; i < list.size(); i++) {
            if (name.equals(list.get(i).name)) {
                list.remove(i);
                Skills.saveAll(App.inst, list);
                Ui.H.post(() -> {
                    MainActivity a = MainActivity.instance();
                    if (a != null && a.workPage() != null) a.workPage().refreshData();
                });
                return "已删除 Skill「" + name + "」";
            }
        }
        throw new Exception("未找到名为「" + name + "」的 Skill，可用 list_skills 查看现有列表");
    }

    private static String listSkills() {
        java.util.List<Skills.S> list = Skills.list(App.inst);
        if (list.isEmpty()) return "(暂无 Skill)";
        StringBuilder sb = new StringBuilder();
        for (Skills.S s : list) {
            sb.append("• ").append(s.name)
              .append(s.enabled ? " [已启用]" : " [未启用]");
            if (!s.desc.isEmpty()) sb.append(" — ").append(s.desc);
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String loadSkill(JSONObject args) throws Exception {
        String name = args.getString("name").trim();
        String detail = Skills.loadSkillDetail(App.inst, name);
        if (detail == null) throw new Exception("未找到已启用的 Skill「" + name + "」，可用 list_skills 查看现有列表");
        return detail;
    }

    private static String createMcp(JSONObject args) throws Exception {
        String name = args.getString("name").trim();
        String url = args.optString("url", "").trim();
        if (name.isEmpty()) throw new Exception("name 不能为空");
        if (!url.startsWith("http")) throw new Exception("url 必须以 http 开头（需为 Streamable HTTP 的 MCP 端点）");
        String hj = args.optString("headers_json", "{}").trim();
        if (hj.isEmpty()) hj = "{}";
        try { new JSONObject(hj); } catch (Exception e) { throw new Exception("headers_json 不是合法的 JSON 对象"); }
        java.util.List<Mcps.Server> list = Mcps.list(App.inst);
        Mcps.Server target = null;
        for (Mcps.Server s : list) if (name.equals(s.name)) { target = s; break; }
        boolean update = target != null;
        if (target == null) { target = Mcps.blank(); target.name = name; list.add(target); }
        target.url = url;
        target.headersJson = hj;
        target.enabled = args.optBoolean("enabled", true);
        StringBuilder sb = new StringBuilder();
        if (target.enabled) {
            try {
                McpClient.forgetSession(target.id);
                McpClient.initialize(target);
                JSONArray tools = McpClient.listTools(target);
                target.tools = tools;
                target.status = "已连接";
                sb.append("连接成功，发现 ").append(tools.length()).append(" 个工具，已挂载可用");
            } catch (Exception e) {
                target.status = "失败: " + e.getMessage();
                sb.append("已保存，但连接测试失败：").append(e.getMessage())
                  .append("（可在 工作台→MCP 中重新测试连接）");
            }
        } else {
            sb.append("已保存（未启用）");
        }
        Mcps.saveAll(App.inst, list);
        Ui.H.post(() -> {
            MainActivity a = MainActivity.instance();
            if (a != null && a.workPage() != null) a.workPage().refreshData();
        });
        return (update ? "已更新" : "已创建") + " MCP 服务器「" + name + "」→ " + url + "\n" + sb;
    }

    private static String deleteMcp(JSONObject args) throws Exception {
        String name = args.getString("name").trim();
        java.util.List<Mcps.Server> list = Mcps.list(App.inst);
        for (int i = 0; i < list.size(); i++) {
            Mcps.Server s = list.get(i);
            if (name.equals(s.name)) {
                McpClient.forgetSession(s.id);
                list.remove(i);
                Mcps.saveAll(App.inst, list);
                Ui.H.post(() -> {
                    MainActivity a = MainActivity.instance();
                    if (a != null && a.workPage() != null) a.workPage().refreshData();
                });
                return "已删除 MCP 服务器「" + name + "」";
            }
        }
        throw new Exception("未找到名为「" + name + "」的 MCP 服务器");
    }

    // ─── 插件管理工具 ───

    /** 创建/更新一张人设卡（AI 直接写入人设库，用户人设栏立即可见可管理） */
    private static String createPersona(JSONObject args) throws Exception {
        String name = args.optString("name", "").trim();
        if (name.isEmpty()) throw new Exception("name 不能为空");
        String emoji = args.optString("emoji", "").trim();
        // 防 emoji：多码点（如表情符号）一律清空，界面用矢量头像兜底
        if (!emoji.isEmpty() && emoji.codePointCount(0, emoji.length()) > 1) emoji = "";
        String desc = args.optString("desc", "").trim();
        String prompt = args.optString("prompt", "").trim();
        java.util.List<Personas.P> list = Personas.list(App.inst);
        boolean updated = false;
        for (Personas.P p : list) {
            if (!p.plugin && p.name.equals(name)) {
                p.emoji = emoji; p.desc = desc; p.prompt = prompt;
                updated = true;
                break;
            }
        }
        String id;
        if (!updated) {
            Personas.P p = Personas.blank();
            p.name = name; p.emoji = emoji; p.desc = desc; p.prompt = prompt;
            list.add(p);
            id = p.id;
        } else {
            id = "";
            for (Personas.P p : list) if (!p.plugin && p.name.equals(name)) { id = p.id; break; }
        }
        Personas.saveAll(App.inst, list);
        refreshChatPersonas();
        return updated
                ? "已更新人设卡「" + name + "」（id=" + id + "）。用户人设栏立即可见。"
                : "已创建人设卡「" + name + "」（id=" + id + "）。用户人设栏立即可见，可编辑/删除。";
    }

    /** 列出所有人设卡（含来源标记），供 AI 管理 */
    private static String listPersonas() {
        java.util.List<Personas.P> list = Personas.listAll(App.inst);
        if (list.isEmpty()) return "(暂无任何人设卡)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            Personas.P p = list.get(i);
            sb.append(i + 1).append(". ").append(p.name);
            if (p.plugin) sb.append(" [来自插件 ").append(p.sourceId).append(" · 只读]");
            else if (p.builtin) sb.append(" [内置]");
            else sb.append(" [自建]");
            if (p.desc != null && !p.desc.isEmpty()) sb.append(" — ").append(p.desc);
            if (p.firstMes != null && !p.firstMes.isEmpty()) sb.append("（含开场白）");
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    /** 删除一张自建/内置人设卡（插件人设只读，不可由本工具删除） */
    private static String deletePersona(JSONObject args) throws Exception {
        String name = args.optString("name", "").trim();
        String id = args.optString("id", "").trim();
        if (name.isEmpty() && id.isEmpty()) throw new Exception("请传入 name 或 id");
        java.util.List<Personas.P> list = Personas.list(App.inst);
        java.util.Iterator<Personas.P> it = list.iterator();
        String removedName = null;
        while (it.hasNext()) {
            Personas.P p = it.next();
            if (p.plugin) continue;
            if ((!id.isEmpty() && id.equals(p.id)) || (!name.isEmpty() && name.equals(p.name))) {
                removedName = p.name;
                it.remove();
                break;
            }
        }
        if (removedName == null) throw new Exception("未找到人设卡「" + (name.isEmpty() ? id : name) + "」，或它是插件提供的只读卡");
        Personas.saveAll(App.inst, list);
        refreshChatPersonas();
        return "已删除人设卡「" + removedName + "」";
    }

    /** 通知聊天页重新加载人设列表（AI 增删人设后界面立即可见） */
    private static void refreshChatPersonas() {
        Ui.H.post(() -> {
            MainActivity ma = MainActivity.instance();
            if (ma == null) return;
            ChatPage cp = ma.chatPage();
            if (cp != null) cp.reloadPersonas();
        });
    }

    private static String installPlugin(JSONObject args) throws Exception {
        String json = args.getString("json").trim();
        if (json.isEmpty()) throw new Exception("json 不能为空");
        String result = Plugins.install(App.inst, json);
        Ui.H.post(() -> {
            MainActivity a = MainActivity.instance();
            if (a != null) a.onPageParamChanged();
        });
        return result + "\n插件可定义：自定义工具(shell/http处理器)、自定义UI页面(声明式布局)、自定义技能(系统提示注入)、自定义人设卡。\n安装后立即生效，无需重启。";
    }

    private static String uninstallPlugin(JSONObject args) throws Exception {
        String id = args.getString("id").trim();
        boolean ok = Plugins.uninstall(App.inst, id);
        if (!ok) throw new Exception("未找到插件「" + id + "」");
        Ui.H.post(() -> {
            MainActivity a = MainActivity.instance();
            if (a != null) a.onPageParamChanged();
        });
        return "已卸载插件「" + id + "」，其所有工具/页面/技能/人设卡已移除";
    }

    private static String listPlugins() {
        java.util.List<Plugins.Plugin> list = Plugins.list(App.inst);
        if (list.isEmpty()) return "(暂无已安装的插件)";
        StringBuilder sb = new StringBuilder();
        for (Plugins.Plugin p : list) {
            sb.append("• ").append(p.name).append(" [").append(p.id).append("]")
              .append(p.enabled ? " [已启用]" : " [已禁用]")
              .append(" v").append(p.version);
            if (!p.desc.isEmpty()) sb.append(" — ").append(p.desc);
            sb.append("\n");
            if (!p.tools.isEmpty()) sb.append("  工具: ").append(p.tools.size()).append(" 个\n");
            if (!p.pages.isEmpty()) sb.append("  页面: ").append(p.pages.size()).append(" 个\n");
            if (!p.skills.isEmpty()) sb.append("  技能: ").append(p.skills.size()).append(" 个\n");
            if (!p.personas.isEmpty()) sb.append("  人设: ").append(p.personas.size()).append(" 个\n");
        }
        return sb.toString();
    }

    private static String enablePlugin(JSONObject args) throws Exception {
        String id = args.getString("id").trim();
        boolean ok = Plugins.setEnabled(App.inst, id, true);
        if (!ok) throw new Exception("未找到插件「" + id + "」");
        Ui.H.post(() -> {
            MainActivity a = MainActivity.instance();
            if (a != null) a.onPageParamChanged();
        });
        return "已启用插件「" + id + "」，其工具/页面/技能/人设卡已生效";
    }

    private static String disablePlugin(JSONObject args) throws Exception {
        String id = args.getString("id").trim();
        boolean ok = Plugins.setEnabled(App.inst, id, false);
        if (!ok) throw new Exception("未找到插件「" + id + "」");
        Ui.H.post(() -> {
            MainActivity a = MainActivity.instance();
            if (a != null) a.onPageParamChanged();
        });
        return "已禁用插件「" + id + "」，其所有功能已暂停";
    }

    // ============================ 浏览器自动化 + 视觉理解 ============================

    private static WebPage webPage() {
        MainActivity act = MainActivity.instance();
        return act == null ? null : act.webPage();
    }

    private static String jsStr(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.isEmpty() || "null".equals(t)) return "";
        try { return new JSONObject("{\"v\":" + t + "}").optString("v"); } catch (Exception e) { return t; }
    }

    private static String trunc(String s, int n) {
        if (s == null) return "";
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }

    private static String browserOpen(JSONObject a) throws Exception {
        WebPage wp = webPage();
        if (wp == null) return "错误：浏览器不可用（请确认已初始化浏览器）";
        String url = a.optString("url", "").trim();
        if (url.isEmpty()) throw new Exception("缺少 url 参数");
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
        wp.focus();
        int wait = a.optInt("waitMs", 8000);
        boolean done = wp.openWait(url, wait);
        StringBuilder out = new StringBuilder("已打开 " + url + (done ? "（加载完成）" : "（仍在加载，可用 browser_status 查看）"));
        String title = jsStr(wp.evalJs("document.title", 1500));
        if (!title.isEmpty()) out.append("\n标题：").append(title);
        return out.toString();
    }

    private static String browserStatus() {
        WebPage wp = webPage();
        if (wp == null) return "错误：浏览器不可用";
        String url = wp.pageUrl();
        String title = jsStr(wp.evalJs("document.title", 1500));
        return "当前页面：" + (url.isEmpty() ? "（未加载或主页）" : url) + (title.isEmpty() ? "" : "\n标题：" + title);
    }

    private static String browserExtract(JSONObject a) {
        WebPage wp = webPage();
        if (wp == null) return "错误：浏览器不可用";
        int max = Math.min(6000, Math.max(200, a.optInt("maxChars", 2500)));
        String js = "(function(){var b=document.body;if(!b)return '';var t=b.innerText||'';"
                + "t=t.replace(/\\s+/g,' ').trim();return t.substring(0," + max + ");})()";
        String r = jsStr(wp.evalJs(js, 2500));
        return "页面文本（" + r.length() + " 字符）：\n" + r;
    }

    private static String browserClick(JSONObject a) throws Exception {
        WebPage wp = webPage();
        if (wp == null) return "错误：浏览器不可用";
        String sel = a.optString("selector", "").trim();
        int x = a.optInt("x", -1), y = a.optInt("y", -1);
        String js;
        if (!sel.isEmpty()) {
            String q = JSONObject.quote(sel);
            js = "(function(){var e=document.querySelector(" + q + ");if(!e)return 'NOT_FOUND';"
                    + "e.scrollIntoView({block:'center'});"
                    + "var r=e.getBoundingClientRect();var cx=r.left+r.width/2,cy=r.top+r.height/2;"
                    + "var n=document.elementFromPoint(cx,cy)||e;"
                    + "['mousedown','mouseup','click'].forEach(function(t){n.dispatchEvent(new MouseEvent(t,{bubbles:true,cancelable:true,view:window,clientX:cx,clientY:cy,button:0}));});"
                    + "return 'OK';})()";
        } else if (x >= 0 && y >= 0) {
            js = "(function(){var cx=" + x + ",cy=" + y + ";"
                    + "var n=document.elementFromPoint(cx,cy);if(!n)return 'NOT_FOUND';"
                    + "['mousedown','mouseup','click'].forEach(function(t){n.dispatchEvent(new MouseEvent(t,{bubbles:true,cancelable:true,view:window,clientX:cx,clientY:cy,button:0}));});"
                    + "return 'OK';})()";
        } else {
            throw new Exception("需提供 selector 或 x/y 坐标");
        }
        String r = jsStr(wp.evalJs(js, 2500));
        return "点击 " + (sel.isEmpty() ? "(" + x + "," + y + ")" : sel) + " → " + (r.isEmpty() ? "OK" : r);
    }

    private static String browserType(JSONObject a) throws Exception {
        WebPage wp = webPage();
        if (wp == null) return "错误：浏览器不可用";
        String sel = a.optString("selector", "").trim();
        if (sel.isEmpty()) throw new Exception("缺少 selector");
        String text = a.optString("text", "");
        String q = JSONObject.quote(sel), t = JSONObject.quote(text);
        String js = "(function(){var e=document.querySelector(" + q + ");if(!e)return 'NOT_FOUND';"
                + "e.focus();"
                + "var de=Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value');"
                + "if(de&&de.set){de.set.call(e," + t + ");}else{e.value=" + t + ";}"
                + "e.dispatchEvent(new Event('input',{bubbles:true}));"
                + "e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "return 'OK';})()";
        String r = jsStr(wp.evalJs(js, 2500));
        return "填写 " + sel + " → " + (r.isEmpty() ? "OK" : r);
    }

    private static String browserScroll(JSONObject a) {
        WebPage wp = webPage();
        if (wp == null) return "错误：浏览器不可用";
        String dir = a.optString("direction", "down").toLowerCase(java.util.Locale.US);
        int amount = Math.max(60, Math.abs(a.optInt("px", 480)));
        String fjs;
        if ("top".equals(dir)) fjs = "window.scrollTo(0,0);'top'";
        else if ("bottom".equals(dir)) fjs = "window.scrollTo(0,document.body.scrollHeight);'bottom'";
        else if ("up".equals(dir)) fjs = "window.scrollBy(0,-" + amount + ");'up'";
        else if ("left".equals(dir)) fjs = "window.scrollBy(-" + amount + ",0);'left'";
        else if ("right".equals(dir)) fjs = "window.scrollBy(" + amount + ",0);'right'";
        else fjs = "window.scrollBy(0," + amount + ");'down'";
        String r = jsStr(wp.evalJs(fjs, 2000));
        return "已滚动 " + dir + (r.isEmpty() ? "" : " → " + r);
    }

    private static String browserBack() {
        WebPage wp = webPage();
        if (wp == null) return "错误：浏览器不可用";
        return wp.goBackAuto() ? "已后退" : "没有可后退的历史页面";
    }

    private static String browserEval(JSONObject a) throws Exception {
        WebPage wp = webPage();
        if (wp == null) return "错误：浏览器不可用";
        String js = a.optString("js", "").trim();
        if (js.isEmpty()) throw new Exception("缺少 js");
        String r = wp.evalJs(js, 3000);
        return "JS 返回值：" + (r == null ? "（执行超时）" : r);
    }

    private static String browserScreenshot(JSONObject a) throws Exception {
        WebPage wp = webPage();
        MainActivity act = MainActivity.instance();
        if (wp == null || act == null) return "错误：浏览器不可用";
        wp.focus();
        try { Thread.sleep(700); } catch (InterruptedException ignored) {}
        android.graphics.Bitmap bmp = wp.screenshot();
        if (bmp == null) return "截图失败（请确保浏览器页处于可见状态）";
        String rel = a.optString("path", "").trim();
        if (rel.contains("/") || rel.contains("\\")) throw new Exception("path 仅允许文件名");
        java.io.File dir = new java.io.File(convWorkspace(), "browsershots");
        dir.mkdirs();
        java.io.File f = new java.io.File(dir, rel.isEmpty()
                ? "auto_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(new java.util.Date()) + ".png"
                : rel);
        java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, fo);
        fo.close();
        return "已保存截图：" + f.getAbsolutePath() + "（" + bmp.getWidth() + "×" + bmp.getHeight() + "）。"
                + "如需理解画面，用 web_vision(path=该文件, question=你想问的问题)";
    }

    private static String browserUa(JSONObject a) {
        WebPage wp = webPage();
        if (wp == null) return "错误：浏览器不可用";
        String ua = a.optString("ua", "").trim();
        if (ua.isEmpty()) {
            ua = "Mozilla/5.0 (Linux; Android 14; Pixel 7; 1080x2400) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
        }
        wp.setUa(ua);
        return "User-Agent 已设置（对后续新加载的页面生效）";
    }

    /** 视觉问答：把截图交给支持视觉的模型理解（云端 OpenAI 兼容 image_url / 本地 Ollama images） */
    private static String webVision(JSONObject a) throws Exception {
        MainActivity act = MainActivity.instance();
        if (act == null) return "错误：应用未就绪";
        String path = a.optString("path", "").trim();
        String q = a.optString("question", "请描述这张截图的主要内容，并给出关键可点击元素的坐标建议");
        java.io.File f = new java.io.File(path);
        if (!f.exists()) return "错误：图片不存在（" + path + "），请先用 browser_screenshot 截图";
        String b64;
        {
            java.io.InputStream in = new java.io.FileInputStream(f);
            byte[] buf = new byte[(int) Math.min(f.length(), 20L * 1024 * 1024)];
            int n = 0;
            while (n < buf.length) {
                int r = in.read(buf, n, buf.length - n);
                if (r < 0) break;
                n += r;
            }
            in.close();
            b64 = android.util.Base64.encodeToString(
                    n == buf.length ? buf : java.util.Arrays.copyOf(buf, n), android.util.Base64.NO_WRAP);
        }
        Prefs p = Prefs.get(act);
        // ===== 视觉模型选择：手动模式优先，其次沿用主模型 =====
        // 手动模式：visionMode=manual 且 visionModelId 非空 → 用独立 visionUrl/visionKey 请求
        // 沿用模式：visionMode=follow（默认）→ 用主模型（activeModel/activeCloudModel）+ 主接口
        boolean manualVision = "manual".equals(p.visionMode())
                && !p.visionModelId().trim().isEmpty();
        String visionModel;
        String visionUrl, visionKey;
        if (manualVision) {
            visionModel = p.visionModelId().trim();
            visionUrl = p.visionUrl().trim();
            visionKey = p.visionKey().trim();
            if (visionUrl.isEmpty()) return "视觉模型手动配置不完整：接口地址为空（设置 → 视觉模型）";
        } else {
            // 沿用主模型：imgVisionModel 兼容旧配置 → activeCloudModel/activeModel → llava
            visionModel = p.imgVisionModel().trim();
            if (visionModel.isEmpty()) {
                if (p.cloudMode()) {
                    visionModel = p.activeCloudModel().isEmpty()
                            ? p.cloudModels().split("[,，]")[0].trim()
                            : p.activeCloudModel();
                } else {
                    visionModel = p.activeModel().isEmpty() ? "llava" : p.activeModel();
                }
            }
            if (visionModel.isEmpty()) visionModel = "llava";
            visionUrl = null;
            visionKey = null;
        }
        int timeout = Math.max(p.timeoutSec(), 30) * 1000;
        // 手动模式 或 云端模式 → OpenAI 兼容 /chat/completions
        if (manualVision || p.cloudMode()) {
            String baseUrl = manualVision ? visionUrl : p.cloudUrl();
            String key = manualVision ? visionKey : p.cloudKey();
            JSONObject body = new JSONObject();
            body.put("model", visionModel);
            body.put("stream", false);
            body.put("max_tokens", 512);
            JSONObject c1 = new JSONObject(); c1.put("type", "text"); c1.put("text", q);
            JSONObject c2 = new JSONObject(); c2.put("type", "image_url");
            JSONObject iu = new JSONObject(); iu.put("url", "data:image/png;base64," + b64);
            c2.put("image_url", iu);
            JSONObject um = new JSONObject(); um.put("role", "user");
            um.put("content", new JSONArray().put(c1).put(c2));
            body.put("messages", new JSONArray().put(um));
            java.util.Map<String, String> hdr = new java.util.HashMap<>();
            if (key != null && !key.isEmpty()) hdr.put("Authorization", "Bearer " + key);
            Http.Resp r = Http.post(Cloud.url(baseUrl, "/chat/completions"), body.toString(), hdr, timeout);
            if (r.code != 200) return "视觉请求失败(" + r.code + ")：" + trunc(r.body, 300);
            JSONObject j = new JSONObject(r.body);
            JSONObject ch = j.optJSONArray("choices").optJSONObject(0);
            JSONObject mm = ch == null ? null : ch.optJSONObject("message");
            return "视觉理解（" + visionModel + "）：\n" + (mm == null ? trunc(r.body, 600) : mm.optString("content", "（无内容）"));
        } else {
            ConvStore.Msg um = new ConvStore.Msg("user", q);
            um.attaches = new java.util.ArrayList<>();
            um.attaches.add(f.getAbsolutePath());
            java.util.List<ConvStore.Msg> msgs = new java.util.ArrayList<>();
            msgs.add(um);
            String body = Ollama.buildChatBody(visionModel, msgs, false, null, p);
            Http.Resp r = Http.post(Ollama.base(p.host(), p.port()) + "/api/chat", body, null, timeout);
            if (r.code != 200) return "视觉请求失败(" + r.code + ")：" + trunc(r.body, 300);
            JSONObject j = new JSONObject(r.body);
            JSONObject mm = j.optJSONObject("message");
            return "视觉理解（" + visionModel + "）：\n" + (mm == null ? trunc(r.body, 600) : mm.optString("content", "（无内容）"));
        }
    }

    /** AI 自主生图：读取设置中生图配置，调用 OpenAI 兼容 /images/generations 接口生成图片并保存到工作区 */
    private static String imageGenerate(JSONObject a) {
        MainActivity act = MainActivity.instance();
        if (act == null) return "错误：应用未就绪";
        try {
            Prefs p = Prefs.get(act);
            if (!p.imgEnabled()) {
                return "生图 AI 未启用：请先在 设置 → 生图 AI 中开启并填写接口地址/密钥/模型（接口需兼容 OpenAI /images/generations）";
            }
            String url = p.imgUrl().trim();
            String key = p.imgKey().trim();
            String model = p.imgModel().trim();
            if (url.isEmpty() || model.isEmpty()) {
                return "生图 AI 配置不完整：接口地址与模型不能为空（设置 → 生图 AI）";
            }
            String prompt = a.optString("prompt", "").trim();
            if (prompt.isEmpty()) throw new Exception("prompt 不能为空");
            String style = a.optString("style", "").trim();
            if (style.isEmpty()) style = p.imgStyle().trim();
            if (!style.isEmpty()) prompt = prompt + "，风格：" + style;
            String size = a.optString("size", "").trim();
            if (size.isEmpty()) size = p.imgSize().trim();
            if (size.isEmpty()) size = "1024x1024";

            String ep = url.endsWith("/images/generations") ? url
                    : url.replaceAll("/+$", "") + "/images/generations";
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("prompt", prompt);
            body.put("n", 1);
            body.put("size", size);
            java.util.Map<String, String> hdr = new java.util.HashMap<>();
            hdr.put("Content-Type", "application/json");
            if (key != null && !key.isEmpty()) hdr.put("Authorization", "Bearer " + key);
            // ModelScope 异步模式 header（标准 OpenAI 端点会忽略此 header，无害）
            hdr.put("X-ModelScope-Async-Mode", "true");
            int timeout = Math.max(p.timeoutSec() * 3, 120) * 1000;
            Http.Resp r = Http.post(ep, body.toString(), hdr, timeout);
            if (r.code != 200) return "生图失败(" + r.code + ")：" + trunc(r.body, 300);

            JSONObject j = new JSONObject(r.body);
            JSONArray data = j.optJSONArray("data");
            String b64 = null;
            String urlOut = null;

            if (data != null && data.length() > 0) {
                // 标准 OpenAI 同步响应：data[0].b64_json 或 data[0].url
                JSONObject d0 = data.optJSONObject(0);
                b64 = d0 == null ? null : d0.optString("b64_json", "");
                urlOut = d0 == null ? null : d0.optString("url", "");
            } else {
                // 异步模式（如 ModelScope）：响应含 task_id，需轮询任务结果
                String taskId = j.optString("task_id", "");
                if (!taskId.isEmpty()) {
                    // 注意：提交响应的 task_status=SUCCEED 仅表示「已受理」，不代表真正完成，
                    // 必须轮询到任务查询接口也返回 SUCCEED 才算完成（否则会拿不到图片 URL）
                    String taskStatus = "";
                    int maxPoll = 60;  // 最多 60 次 × 5 秒 = 300 秒（5 分钟，ModelScope 免费排队可达 2-4 分钟）
                    for (int poll = 0; poll < maxPoll; poll++) {
                        try { Thread.sleep(5000); } catch (InterruptedException ie) { break; }
                        String taskEp = url.replaceAll("/images/generations$", "")
                                .replaceAll("/+$", "") + "/tasks/" + taskId;
                        // ModelScope 任务查询需要 X-ModelScope-Task-Type header
                        java.util.Map<String, String> taskHdr = new java.util.HashMap<>(hdr);
                        taskHdr.put("X-ModelScope-Task-Type", "image_generation");
                        Http.Resp tr = Http.get(taskEp, taskHdr, 30000);
                        if (tr.code != 200) return "查询生图任务失败(" + tr.code + ")：" + trunc(tr.body, 300);
                        JSONObject tj;
                        try { tj = new JSONObject(tr.body); }
                        catch (Exception je) { continue; }
                        taskStatus = tj.optString("task_status", "");
                        if ("FAILED".equals(taskStatus) || "ERROR".equals(taskStatus)) {
                            return "生图任务失败：" + trunc(tr.body, 300);
                        }
                        if ("SUCCEED".equals(taskStatus) || "SUCCESS".equals(taskStatus)) {
                            // ModelScope: output_images 字符串数组
                            JSONArray outImgs = tj.optJSONArray("output_images");
                            if (outImgs != null && outImgs.length() > 0) {
                                urlOut = outImgs.optString(0, "");
                            }
                            // 标准 OpenAI: data[0].url
                            if ((urlOut == null || urlOut.isEmpty()) && tj.optJSONArray("data") != null) {
                                JSONArray td = tj.optJSONArray("data");
                                JSONObject d0 = td.optJSONObject(0);
                                urlOut = d0 == null ? null : d0.optString("url", "");
                            }
                            // 其他格式: output.images[0].url 或 output.url
                            if (urlOut == null || urlOut.isEmpty()) {
                                JSONObject output = tj.optJSONObject("output");
                                if (output != null) {
                                    JSONArray imgs = output.optJSONArray("images");
                                    if (imgs != null && imgs.length() > 0) {
                                        JSONObject img0 = imgs.optJSONObject(0);
                                        urlOut = img0 == null ? imgs.optString(0, "") : img0.optString("url", "");
                                    }
                                    if (urlOut == null || urlOut.isEmpty()) urlOut = output.optString("url", "");
                                }
                            }
                            break;
                        }
                    }
                    if (urlOut == null || urlOut.isEmpty()) {
                        if ("SUCCEED".equals(taskStatus) || "SUCCESS".equals(taskStatus)) {
                            return "生图任务已成功但响应未包含图片 URL，请用相同 prompt 重试：" + trunc(r.body, 300);
                        }
                        return "生图任务仍在处理中（排队较长），已等待约 5 分钟。任务ID=" + taskId
                                + "，可用相同 prompt 重试一次。";
                    }
                } else {
                    return "生图失败：响应无 data 且无 task_id：" + trunc(r.body, 300);
                }
            }

            String dirRel = p.imgDir().trim();
            if (dirRel.isEmpty()) dirRel = "images";
            java.io.File dir = resolve(dirRel);
            if (!dir.exists()) dir.mkdirs();
            String outName = a.optString("out", "").trim();
            if (outName.isEmpty()) outName = "gen_" + System.currentTimeMillis() + ".png";
            if (!outName.endsWith(".png") && !outName.endsWith(".jpg") && !outName.endsWith(".jpeg") && !outName.endsWith(".webp")) {
                outName = outName + ".png";
            }
            java.io.File outF = new java.io.File(dir, outName);

            if (b64 != null && !b64.isEmpty()) {
                byte[] bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                java.io.FileOutputStream fo = new java.io.FileOutputStream(outF);
                fo.write(bytes);
                fo.close();
            } else if (urlOut != null && !urlOut.isEmpty()) {
                // 直接以二进制流下载，避免 Http.readAll 的 UTF-8 解码破坏图片字节
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(urlOut).openConnection();
                c.setConnectTimeout(60000);
                c.setReadTimeout(600000);
                int code = c.getResponseCode();
                if (code != 200) return "生图成功但下载图片失败(" + code + ")";
                java.io.InputStream in = c.getInputStream();
                java.io.FileOutputStream fo = new java.io.FileOutputStream(outF);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
                in.close();
                fo.close();
                c.disconnect();
            } else {
                return "生图失败：响应既无 b64_json 也无 url：" + trunc(r.body, 300);
            }
            return "图片已生成并保存：" + outF.getAbsolutePath()
                    + "\n（在回复中展示该图片请用 Markdown 图片语法：![图片描述](" + outF.getAbsolutePath()
                    + ")，气泡会自动渲染成图片；也可以继续用其他工具处理）";
        } catch (Exception e) {
            return "[生图失败] " + e.getMessage();
        }
    }

    /** AI 自主会话命名：重命名当前会话标题 */
    private static String renameConv(JSONObject a) {
        MainActivity act = MainActivity.instance();
        if (act == null) return "错误：应用未就绪";
        String title = a.optString("title", "").trim();
        if (title.isEmpty()) return "错误：title 不能为空";
        if (title.length() > 18) title = title.substring(0, 17) + "…";
        ChatPage cp = act.chatPage();
        if (cp == null) return "错误：会话页未就绪";
        cp.renameConv(title);
        return "会话已重命名为：「" + title + "」";
    }
}
