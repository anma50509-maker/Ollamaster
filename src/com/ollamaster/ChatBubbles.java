package com.ollamaster;

import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * ChatPage 的消息气泡构建与列表适配器：用户气泡 / AI 气泡（思考折叠+富文本表格）/ 工具与通知小卡片。
 * 从 ChatPage 拆出（原 2843 行巨型文件）。气泡构建时向 ChatPage.streamViews 注册流式刷新句柄。
 */
class ChatBubbles {
    final ChatPage cp;
    final MainActivity act;

    /** 适配器只读快照：仅 UI 线程经 fill() 写入，适配器回调只读。
     *  这样后台线程（工具/流式/记忆写入）改动 conv.msgs，不会破坏 ListView
     *  在 getCount/getItemViewType/getView 之间的一致性 —— 修复
     *  IndexOutOfBoundsException 与 "adapter has changed but did not receive a
     *  notification" 两类崩溃。所有可见性更新统一走 ChatPage.syncAdapter()。 */
    final java.util.ArrayList<ConvStore.Msg> rows = new java.util.ArrayList<>();

    boolean inRange(int p) { return p >= 0 && p < rows.size(); }

    /** UI 线程：先快照、后通知（由 ChatPage.syncAdapter 调用） */
    void fill(java.util.List<ConvStore.Msg> src) {
        rows.clear();
        if (src != null) rows.addAll(src);
    }

    ChatBubbles(ChatPage cp) {
        this.cp = cp;
        this.act = cp.act;
    }

    /** 虚拟化消息列表：ListView 只测量/布局屏幕内的气泡，历史长度不再拖慢流式刷新 */
    BaseAdapter adapter() {
        return new BaseAdapter() {
            @Override public int getCount() { return rows.size(); }
            @Override public Object getItem(int position) { return inRange(position) ? rows.get(position) : null; }
            @Override public long getItemId(int position) { return position; }
            @Override public int getViewTypeCount() { return 3; }
            @Override public int getItemViewType(int position) {
                ConvStore.Msg mm = inRange(position) ? rows.get(position) : null;
                if (mm == null) return 2;
                String r = mm.role;
                if ("user".equals(r)) return 0;
                if ("assistant".equals(r)) return 1;
                return 2;
            }
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                final ConvStore.Msg m = inRange(position) ? rows.get(position) : null;
                if (m == null) return new TextView(act);
                try {
                    String r = m.role;
                    if ("user".equals(r)) return buildUserBubble(m);
                    if ("assistant".equals(r)) return buildAiBubble(m);
                    return buildSmallCard(m);
                } catch (Throwable e) {
                    ErrLog.log(act, "bubble.getView", e);
                    TextView fb = new TextView(act);
                    fb.setText(m.content == null ? "" : m.content);
                    fb.setTextColor(cp.t.textPri);
                    fb.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.spi(act, 14.5f));
                    int pad = Ui.dpi(act, 10);
                    fb.setPadding(pad, pad, pad, pad);
                    return fb;
                }
            }
        };
    }

    private View buildUserBubble(final ConvStore.Msg m) {
        Theme t = cp.t;
        LinearLayout wrap = new LinearLayout(act);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(Gravity.END);

        boolean hasAtt = m.attaches != null && !m.attaches.isEmpty();
        if (hasAtt) {
            LinearLayout chips = new LinearLayout(act);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            for (String p : m.attaches) {
                TextView chip = new TextView(act);
                chip.setText(ChatPage.attachLabel(p));
                Icon.pinLeft(chip, ChatPage.attachKind(p), 12);
                chip.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 10.5f));
                chip.setTextColor(t.textSec);
                chip.setSingleLine(true);
                int cpad = Ui.dpi(act, 7);
                chip.setPadding(cpad, Ui.dpi(act, 3), cpad, Ui.dpi(act, 3));
                chip.setBackground(Ui.round(t.alpha(t.textPri, 0.07f), Ui.dpi(act, 999)));
                chips.addView(chip);
                LinearLayout.LayoutParams clp = (LinearLayout.LayoutParams) chip.getLayoutParams();
                clp.leftMargin = Ui.dpi(act, 5);
                chip.setLayoutParams(clp);
            }
            wrap.addView(chips, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        TextView tv = new TextView(act);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.spi(act, 14.5f));
        tv.setLineSpacing(0, 1.25f);
        int pad = Ui.dpi(act, 13);
        tv.setPadding(pad, pad - 3, pad, pad - 3);
        tv.setTextColor(t.mixTextOn(t));
        tv.setText(m.content);
        tv.setOnLongClickListener(vv -> {
            cp.dialogs.msgMenu(m, false);
            return true;
        });
        tv.setHighlightColor(0x55FFFFFF);
        tv.setBackground(Ui.radii(t.alpha(t.accent, 0.92f), Ui.dpi(act, 17),
                Ui.dpi(act, 4), Ui.dpi(act, 17), Ui.dpi(act, 17)));
        tv.setMaxWidth(Ui.dpi(act, 272));
        row.addView(tv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tv.getLayoutParams();
        lp.topMargin = Ui.dpi(act, 5);
        lp.bottomMargin = Ui.dpi(act, 5);
        tv.setLayoutParams(lp);

        // 时间戳行：同时提供长按消息菜单入口（文本选择模式已占用地板长按）
        TextView umeta = new TextView(act);
        umeta.setText(cp.tf.format(new Date(m.ts)));
        umeta.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 9.5f));
        umeta.setTextColor(t.alpha(t.textSec, 0.9f));
        umeta.setPadding(0, 0, Ui.dpi(act, 6), 0);
        umeta.setGravity(Gravity.END);
        umeta.setOnLongClickListener(vv -> {
            cp.dialogs.msgMenu(m, false);
            return true;
        });
        wrap.addView(umeta, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        wrap.setOnLongClickListener(vv -> {
            cp.dialogs.msgMenu(m, false);
            return true;
        });
        return wrap;
    }

    private View buildAiBubble(final ConvStore.Msg m) {
        Theme t = cp.t;
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);

        FrameLayout avatar = new FrameLayout(act);
        avatar.setBackground(Ui.stroke(t.alpha(t.accent, 0.12f), t.alpha(t.accent, 0.45f),
                Ui.dpi(act, 999), Ui.dpi(act, 0.9f)));
        ImageView avImg = new ImageView(act);
        Drawable avd = cp.persona != null ? cp.loadAvatar(cp.persona.avatar, 16) : null;
        avImg.setImageDrawable(avd != null ? avd : Icon.v(act, "avatar", t.accent, 14));
        avatar.addView(avImg, new FrameLayout.LayoutParams(Ui.dpi(act, 16), Ui.dpi(act, 16), Gravity.CENTER));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Ui.dpi(act, 24), Ui.dpi(act, 24));
        alp.topMargin = Ui.dpi(act, 6);
        alp.rightMargin = Ui.dpi(act, 8);
        row.addView(avatar, alp);

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView think = new TextView(act);
        think.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11.5f));
        think.setLineSpacing(0, 1.2f);
        think.setTextColor(t.alpha(t.textPri, 0.55f));
        int tpad = Ui.dpi(act, 10);
        think.setPadding(tpad, tpad - 2, tpad, tpad - 2);
        think.setBackground(Ui.round(t.alpha(t.textPri, 0.05f), Ui.dpi(act, 10)));
        think.setVisibility(View.GONE);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.bottomMargin = Ui.dpi(act, 5);
        tlp.rightMargin = Ui.dpi(act, 34);
        col.addView(think, tlp);

        String raw = m.content == null ? "" : m.content;
        boolean showThink = Prefs.get(act).showThink();
        String thinkText = null, answerText;
        int ta = ChatPage.idxOf(raw, "<think>");
        if (showThink && ta >= 0) {
            int tb = ChatPage.idxOf(raw, "</think>", ta + 7);
            thinkText = tb >= 0 ? raw.substring(ta + 7, tb) : raw.substring(ta + 7);
            answerText = tb >= 0 ? raw.substring(tb + 8) : "";
        } else {
            answerText = ChatPage.stripThink(raw);
        }
        if (showThink && (thinkText == null || thinkText.trim().isEmpty())) {
            thinkText = m.reasoning;
        }
        if (thinkText != null && !thinkText.trim().isEmpty()) {
            applyThinkBlock(think, "think|" + m.ts, thinkText);
        }

        // 工具调用轮次：content 为空但有工具调用时，显示工具调用摘要，避免空消息观感
        if (answerText.trim().isEmpty() && m.tools != null && !m.tools.isEmpty()) {
            StringBuilder tsb = new StringBuilder();
            for (int ti = 0; ti < m.tools.size() && ti < 3; ti++) {
                if (ti > 0) tsb.append("、");
                tsb.append(m.tools.get(ti).name);
            }
            if (m.tools.size() > 3) tsb.append(" 等");
            answerText = "[调用工具 " + tsb + "]";
        }

        // 正文容器：统一圆角背景，内部按 flow 顺序渲染文本块与表格/图片视图（视图停留在原文位置）
        LinearLayout bodyBox = new LinearLayout(act);
        bodyBox.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dpi(act, 13);
        bodyBox.setPadding(pad, pad - 3, pad, pad - 3);
        bodyBox.setBackground(Ui.radii(t.surfaceAlt, Ui.dpi(act, 4), Ui.dpi(act, 17),
                Ui.dpi(act, 17), Ui.dpi(act, 17)));

        Markdown.RichResult rr;
        try {
            if (!cp.streaming) {
                // 完整答案：文本块与表格/图片按原文顺序穿插渲染
                rr = Markdown.prepareRich(act, answerText, t);
            } else {
                // 流式：实时 MD 渲染为单个文本块，后续由 refreshStreamingBubble 持续更新
                rr = new Markdown.RichResult();
                rr.flow.add(Markdown.render(act, answerText.isEmpty() ? "▍" : answerText, t));
            }
        } catch (Throwable e) {
            rr = new Markdown.RichResult();
            rr.flow.add(answerText);
        }

        TextView firstText = null;
        for (Object seg : rr.flow) {
            if (seg instanceof View) {
                LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                vlp.topMargin = Ui.dpi(act, 2);
                bodyBox.addView((View) seg, vlp);
            } else {
                TextView chunk = new TextView(act);
                chunk.setText((CharSequence) seg);
                chunk.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.spi(act, 14.5f));
                chunk.setLineSpacing(0, 1.3f);
                chunk.setHighlightColor(t.alpha(t.accent, 0.26f));
                chunk.setTextColor(t.textPri);
                chunk.setMaxWidth(Ui.dpi(act, 272));
                if (firstText == null) {
                    firstText = chunk;
                    bodyBox.addView(chunk, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                } else {
                    LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    clp.topMargin = Ui.dpi(act, 6);
                    bodyBox.addView(chunk, clp);
                }
            }
        }
        if (firstText == null) {
            firstText = new TextView(act);
            firstText.setText("");
            firstText.setVisibility(View.GONE);
            bodyBox.addView(firstText);
        }
        bodyBox.setVisibility(answerText.isEmpty() && !cp.streaming ? View.GONE : View.VISIBLE);
        bodyBox.setOnLongClickListener(vv -> {
            cp.dialogs.msgMenu(m, true);
            return true;
        });
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyLp.rightMargin = Ui.dpi(act, 34);
        col.addView(bodyBox, bodyLp);

        TextView meta = new TextView(act);
        StringBuilder mt = new StringBuilder(cp.tf.format(new Date(m.ts)));
        if (m.evalTokens > 0 && m.tps > 0) mt.append(" · ").append(m.evalTokens)
                .append(" tok · ").append(String.format(Locale.US, "%.1f tok/s", m.tps));
        mt.append(" · ").append(m.model != null && !m.model.isEmpty() ? m.model : cp.modelShort());
        if (m.promptTokens > 0) {
            mt.append(" · 输入 ").append(m.promptTokens);
            long hit = m.cacheHitTokens;
            long miss = m.cacheMissTokens;
            if (hit > 0) {
                long total = hit + miss;
                if (total <= 0) total = m.promptTokens;
                int rate = (int)(hit * 100 / total);
                mt.append(" · 缓存").append(rate).append("%");
            } else if (miss > 0) {
                mt.append(" · 缓存0%");
            }
        }
        meta.setText(mt);
        meta.setSingleLine(true);
        meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        meta.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 9.5f));
        meta.setTextColor(t.alpha(t.textSec, 0.9f));
        meta.setPadding(Ui.dpi(act, 5), Ui.dpi(act, 3), Ui.dpi(act, 4), 0);
        meta.setOnLongClickListener(v -> {
            cp.dialogs.msgMenu(m, true);
            return true;
        });
        col.addView(meta);

        // 朗读按钮：聊天页直接点击播放本条回复（无需长按进菜单）；流式生成中的气泡不显示
        if (!cp.streaming || m != cp.streamMsg) {
            final String speechForBtn = ChatPage.stripForSpeech(m.content);
            if (!speechForBtn.isEmpty()) {
                LinearLayout actRow = new LinearLayout(act);
                actRow.setOrientation(LinearLayout.HORIZONTAL);
                TextView speakBtn = new TextView(act);
                speakBtn.setText("朗读");
                speakBtn.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 10.5f));
                speakBtn.setTextColor(t.accent);
                speakBtn.setPadding(Ui.dpi(act, 9), Ui.dpi(act, 3), Ui.dpi(act, 9), Ui.dpi(act, 3));
                speakBtn.setBackground(Ui.round(t.alpha(t.accent, 0.10f), Ui.dpi(act, 9)));
                speakBtn.setCompoundDrawablePadding(Ui.dpi(act, 3));
                Icon.pinLeft(speakBtn, "voice", 12);
                speakBtn.setOnClickListener(v -> {
                    TtsEngine.get(act).speak(speechForBtn);
                    Ui.toast(act, "正在朗读本条回复…");
                });
                actRow.addView(speakBtn);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                slp.topMargin = Ui.dpi(act, 4);
                slp.leftMargin = Ui.dpi(act, 2);
                col.addView(actRow, slp);
            }
        }

        row.addView(col, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        row.setTag(new ChatPage.AiHolder(think, firstText, bodyBox));
        // 登记到流式刷新注册表：流式期间重建列表后仅重新绑定正在生成的气泡，避免误绑旧消息
        if (!cp.streaming || m == cp.streamMsg) cp.streamViews.put(System.identityHashCode(m), new ChatPage.AiHolder(think, firstText, bodyBox));
        if (cp.pendingRegister && m == cp.streamMsg) { cp.pendingRegister = false; cp.markDirty(); }
        row.setOnLongClickListener(vv -> {
            cp.dialogs.msgMenu(m, true);
            return true;
        });
        return row;
    }

    private static final java.util.HashSet<String> expandedCards = new java.util.HashSet<>();

    private View buildSmallCard(final ConvStore.Msg m) {
        Theme t = cp.t;
        LinearLayout wrap = new LinearLayout(act);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(Ui.dpi(act, 30), Ui.dpi(act, 3), Ui.dpi(act, 8), Ui.dpi(act, 3));

        boolean isTool = "tool".equals(m.role);
        final String key = m.ts + "|" + m.role + "|" + (m.toolName == null ? "" : m.toolName);
        // 渲染兜底：工具返回 / 运行提示里可能混入模型写进正文的 <think> 段，展示前统一剥离
        String shown0 = ChatPage.stripThink(m.content == null ? "" : m.content);
        String shownTrim = shown0.trim();
        // 剥离思考段后可能留下悬空的连接符（如「执行中 1/1：x →」），收尾清理
        if (shownTrim.endsWith("→")) shownTrim = shownTrim.substring(0, shownTrim.length() - 1).trim();
        else if (shownTrim.endsWith("->")) shownTrim = shownTrim.substring(0, shownTrim.length() - 2).trim();
        final String shownText = shownTrim.isEmpty() ? shown0 : shownTrim;

        // 多智能体结果卡片：有运行轨迹时渲染「群聊卡片 / 子 Agent 卡片」，无轨迹则回退纯文本小卡片
        if (isTool && m.toolName != null && m.toolCallId != null
                && ("team_run".equals(m.toolName) || "subagent_run".equals(m.toolName))) {
            final AgentActivity.Run run = AgentActivity.get(m.toolCallId);
            if (run != null && (!run.turns.isEmpty() || !run.summary.isEmpty())) {
                try {
                    return "team_run".equals(m.toolName)
                            ? buildTeamCard(m, run, key) : buildSubAgentCard(m, run, key);
                } catch (Throwable e) {
                    ErrLog.log(act, "bubble.agentCard", e);
                }
            }
        }
        TextView card = new TextView(act);
        card.setTextColor(isTool ? t.alpha(t.accent, 0.95f) : t.alpha(t.danger, 0.95f));
        card.setTypeface(isTool ? Ui.mono() : Typeface.DEFAULT);
        card.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11));
        card.setBackground(Ui.round(t.alpha(isTool ? t.accent : t.danger, 0.07f), Ui.dpi(act, 10)));
        int cpad = Ui.dpi(act, 9);
        card.setPadding(cpad, cpad - 3, cpad, cpad - 3);

        Runnable apply = () -> {
            boolean expanded = expandedCards.contains(key);
            if (isTool && !expanded) {
                String head = shownText.replace('\n', ' ').trim();
                if (head.length() > 60) head = head.substring(0, 60) + "…";
                card.setText("[" + m.toolName + "] " + head);
                card.setMaxLines(1);
            } else {
                card.setText((isTool ? "[" + m.toolName + "]\n" : "")
                        + (shownText.trim().isEmpty() ? "(无正文)" : shownText));
                card.setMaxLines(Integer.MAX_VALUE);
            }
        };
        apply.run();
        wrap.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (isTool) wrap.setOnClickListener(v -> {
            if (!expandedCards.remove(key)) expandedCards.add(key);
            apply.run();
        });
        wrap.setOnLongClickListener(vv -> {
            cp.dialogs.msgMenu(m, false);
            return true;
        });
        return wrap;
    }


    /** 团队群聊卡片：角色头像行 + 逐条发言气泡 + 协调者汇总（默认只露最新两条，点击展开全部） */
    private View buildTeamCard(final ConvStore.Msg m, final AgentActivity.Run run, final String key) {
        Theme t = cp.t;
        final boolean expanded = expandedCards.contains(key);

        LinearLayout wrap = new LinearLayout(act);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(Ui.dpi(act, 22), Ui.dpi(act, 4), Ui.dpi(act, 10), Ui.dpi(act, 4));

        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.stroke(t.surfaceAlt, t.border, Ui.dpi(act, 14), Ui.dpi(act, 0.7f)));
        int pad = Ui.dpi(act, 11);
        card.setPadding(pad, pad, pad, pad);

        // 头部：标题 + 模式徽标 + 元信息
        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(act);
        title.setText("Agent 工作群聊");
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 13));
        title.setTextColor(t.textPri);
        head.addView(title);

        TextView mode = new TextView(act);
        mode.setText(run.mode);
        mode.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 10));
        mode.setTextColor(t.accentDeep);
        mode.setPadding(Ui.dpi(act, 7), Ui.dpi(act, 1), Ui.dpi(act, 7), Ui.dpi(act, 1));
        mode.setBackground(Ui.round(t.alpha(t.accent, 0.14f), Ui.dpi(act, 999)));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.leftMargin = Ui.dpi(act, 7);
        head.addView(mode, mlp);

        TextView meta = new TextView(act);
        StringBuilder mt = new StringBuilder();
        mt.append(run.roles.isEmpty() ? run.done + " 步" : run.roles.size() + " 角色 · " + run.done + " 步");
        mt.append(" · ").append(String.format(Locale.US, "%.0fs", run.elapsedMs() / 1000.0));
        if (run.calls > 0) mt.append(" · ").append(run.calls).append(" 次调用");
        meta.setText(mt);
        meta.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 10));
        meta.setTextColor(t.alpha(t.textSec, 0.95f));
        meta.setSingleLine(true);
        meta.setGravity(Gravity.END);
        meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams mtlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mtlp.leftMargin = Ui.dpi(act, 6);
        head.addView(meta, mtlp);
        card.addView(head);

        if ("failed".equals(run.status)) {
            TextView fail = new TextView(act);
            fail.setText("运行中断，以下为已完成的部分发言");
            fail.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 10.5f));
            fail.setTextColor(t.alpha(t.danger, 0.95f));
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            flp.topMargin = Ui.dpi(act, 6);
            card.addView(fail, flp);
        }

        // 角色头像行：一人一色，按角色名稳定取色
        if (!run.roles.isEmpty()) {
            LinearLayout avatars = new LinearLayout(act);
            avatars.setOrientation(LinearLayout.HORIZONTAL);
            for (String role : run.roles) {
                int c = cp.roleColor(role);
                LinearLayout col = new LinearLayout(act);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setGravity(Gravity.CENTER_HORIZONTAL);
                TextView av = new TextView(act);
                av.setText(role.isEmpty() ? "?" : role.substring(0, 1));
                av.setGravity(Gravity.CENTER);
                av.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11));
                av.setTextColor(c);
                av.setBackground(Ui.round(t.alpha(c, 0.15f), Ui.dpi(act, 999)));
                col.addView(av, new LinearLayout.LayoutParams(Ui.dpi(act, 30), Ui.dpi(act, 30)));
                TextView nm = new TextView(act);
                nm.setText(role);
                nm.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 10));
                nm.setTextColor(t.textSec);
                nm.setSingleLine(true);
                nm.setEllipsize(android.text.TextUtils.TruncateAt.END);
                nm.setGravity(Gravity.CENTER);
                col.addView(nm);
                avatars.addView(col, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            alp.topMargin = Ui.dpi(act, 10);
            alp.bottomMargin = Ui.dpi(act, 2);
            card.addView(avatars, alp);
        }

        // 发言：默认只显示最新两条，更早的折进可展开容器（保持时间顺序）
        int n = run.turns.size();
        int hiddenCount = expanded ? 0 : Math.max(0, n - 2);
        final LinearLayout older = new LinearLayout(act);
        older.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < hiddenCount; i++) older.addView(turnRow(run.turns.get(i), expanded));
        older.setVisibility(View.GONE);
        card.addView(older);

        for (int i = hiddenCount; i < n; i++) card.addView(turnRow(run.turns.get(i), expanded));

        final TextView summaryBody = buildSummaryBlock(run, expanded, card);

        if (hiddenCount > 0 || (summaryBody != null)) {
            final TextView toggle = new TextView(act);
            final boolean hasOlder = hiddenCount > 0;
            toggle.setText(expanded ? "收起" : "展开全部");
            toggle.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11));
            toggle.setTextColor(t.alpha(t.accent, 0.95f));
            toggle.setPadding(0, Ui.dpi(act, 7), 0, 0);
            LinearLayout.LayoutParams tglp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tglp.topMargin = Ui.dpi(act, 2);
            toggle.setOnClickListener(v -> {
                boolean ex = expandedCards.contains(key);
                if (ex) expandedCards.remove(key); else expandedCards.add(key);
                boolean now = !ex;
                if (hasOlder) older.setVisibility(now ? View.VISIBLE : View.GONE);
                for (int i = 0; i < older.getChildCount(); i++) {
                    applyTurnLines(older.getChildAt(i), now);
                }
                if (summaryBody != null) {
                    summaryBody.setMaxLines(now ? 60 : 6);
                    summaryBody.setEllipsize(now ? null : android.text.TextUtils.TruncateAt.END);
                }
                toggle.setText(now ? "收起" : "展开全部");
            });
            card.addView(toggle, tglp);
        }

        wrap.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.setOnLongClickListener(vv -> {
            cp.dialogs.msgMenu(m, true);
            return true;
        });
        return wrap;
    }

    /** 汇总块：协调者的最终交付物（默认 6 行，随卡片展开） */
    private TextView buildSummaryBlock(final AgentActivity.Run run, boolean expanded, LinearLayout card) {
        if (run.summary == null || run.summary.trim().isEmpty()) return null;
        Theme t = cp.t;
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.stroke(t.alpha(t.accent, 0.07f), t.alpha(t.accent, 0.18f),
                Ui.dpi(act, 10), Ui.dpi(act, 0.7f)));
        int bp = Ui.dpi(act, 10);
        box.setPadding(bp, Ui.dpi(act, 8), bp, Ui.dpi(act, 8));

        TextView label = new TextView(act);
        label.setText("最终交付物");
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 10.5f));
        label.setTextColor(t.accentDeep);
        box.addView(label);

        TextView body = new TextView(act);
        String sumShown = ChatPage.stripThink(run.summary);
        try {
            body.setText(Markdown.render(act, sumShown, t));
        } catch (Throwable e) {
            body.setText(sumShown);
        }
        body.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 13));
        body.setLineSpacing(0, 1.35f);
        body.setTextColor(t.textPri);
        body.setMaxLines(expanded ? 60 : 6);
        body.setEllipsize(expanded ? null : android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dpi(act, 4);
        box.addView(body, blp);

        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        boxLp.topMargin = Ui.dpi(act, 9);
        card.addView(box, boxLp);
        return body;
    }

    /** 单条角色发言：头像 + 「角色 · 第 N 轮」 + 内容（默认 4 行，随卡片展开） */
    private View turnRow(AgentActivity.Turn turn, boolean expanded) {
        Theme t = cp.t;
        int c = cp.roleColor(turn.role);

        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dpi(act, 7);
        row.setLayoutParams(rlp);

        TextView av = new TextView(act);
        av.setText(turn.role.isEmpty() ? "?" : turn.role.substring(0, 1));
        av.setGravity(Gravity.CENTER);
        av.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 10));
        av.setTextColor(c);
        av.setBackground(Ui.round(t.alpha(c, 0.15f), Ui.dpi(act, 999)));
        row.addView(av, new LinearLayout.LayoutParams(Ui.dpi(act, 22), Ui.dpi(act, 22)));

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView nm = new TextView(act);
        nm.setText(turn.role + (turn.round > 0 ? "  ·  第 " + turn.round + " 轮" : ""));
        nm.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 10.5f));
        nm.setTextColor(c);
        nm.setSingleLine(true);
        col.addView(nm);

        TextView tx = new TextView(act);
        // 角色产出可能带 <think> 包装，卡片里只展示正文（与主气泡一致，避免裸标签破坏观感）
        String shown = ChatPage.stripThink(turn.text);
        if (shown.trim().isEmpty()) shown = "(本条无正文)";
        try {
            tx.setText(Markdown.render(act, shown, t));
        } catch (Throwable e) {
            tx.setText(shown);
        }
        tx.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 13));
        tx.setLineSpacing(0, 1.3f);
        tx.setTextColor(t.textPri);
        tx.setMaxLines(expanded ? 40 : 4);
        tx.setEllipsize(expanded ? null : android.text.TextUtils.TruncateAt.END);
        tx.setTag("turnText");
        col.addView(tx);

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clp.leftMargin = Ui.dpi(act, 7);
        row.addView(col, clp);
        return row;
    }

    /** 展开/收起时同步调整某条发言的行数上限 */
    private void applyTurnLines(View row, boolean expanded) {
        if (!(row instanceof ViewGroup)) return;
        ViewGroup g = (ViewGroup) row;
        for (int i = 0; i < g.getChildCount(); i++) {
            View child = g.getChildAt(i);
            if (child instanceof TextView && "turnText".equals(child.getTag())) {
                TextView tv = (TextView) child;
                tv.setMaxLines(expanded ? 40 : 4);
                tv.setEllipsize(expanded ? null : android.text.TextUtils.TruncateAt.END);
            } else {
                applyTurnLines(child, expanded);
            }
        }
    }

    /** 子 Agent 卡片：独立上下文标识 + 轮次/耗时 + 结论（默认折叠 5 行） */
    private View buildSubAgentCard(final ConvStore.Msg m, final AgentActivity.Run run, final String key) {
        Theme t = cp.t;
        final boolean expanded = expandedCards.contains(key);

        LinearLayout wrap = new LinearLayout(act);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(Ui.dpi(act, 22), Ui.dpi(act, 4), Ui.dpi(act, 10), Ui.dpi(act, 4));

        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.stroke(t.surfaceAlt, t.border, Ui.dpi(act, 14), Ui.dpi(act, 0.7f)));
        int pad = Ui.dpi(act, 11);
        card.setPadding(pad, pad, pad, pad);

        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView av = new TextView(act);
        av.setText("子");
        av.setGravity(Gravity.CENTER);
        av.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 10.5f));
        av.setTextColor(t.textSec);
        av.setBackground(Ui.stroke(android.graphics.Color.TRANSPARENT, t.alpha(t.border, 0.9f),
                Ui.dpi(act, 999), Ui.dpi(act, 1f)));
        head.addView(av, new LinearLayout.LayoutParams(Ui.dpi(act, 24), Ui.dpi(act, 24)));

        TextView title = new TextView(act);
        title.setText("子 Agent");
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 13));
        title.setTextColor(t.textPri);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = Ui.dpi(act, 8);
        head.addView(title, tlp);

        TextView tag = new TextView(act);
        tag.setText("独立上下文");
        tag.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 10));
        tag.setTextColor(t.textSec);
        tag.setPadding(Ui.dpi(act, 6), Ui.dpi(act, 1), Ui.dpi(act, 6), Ui.dpi(act, 1));
        tag.setBackground(Ui.round(t.alpha(t.textPri, 0.07f), Ui.dpi(act, 999)));
        LinearLayout.LayoutParams tgl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tgl.leftMargin = Ui.dpi(act, 6);
        head.addView(tag, tgl);

        TextView meta = new TextView(act);
        StringBuilder mt = new StringBuilder();
        if (!run.note.isEmpty()) mt.append(run.note).append(" · ");
        mt.append(String.format(Locale.US, "%.1fs", run.elapsedMs() / 1000.0));
        if (run.calls > 0) mt.append(" · ").append(run.calls).append(" 次调用");
        meta.setText(mt);
        meta.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 10));
        meta.setTextColor(t.alpha(t.textSec, 0.95f));
        meta.setSingleLine(true);
        meta.setGravity(Gravity.END);
        meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mlp.leftMargin = Ui.dpi(act, 6);
        head.addView(meta, mlp);
        card.addView(head);

        if ("failed".equals(run.status)) {
            TextView fail = new TextView(act);
            fail.setText("子 Agent 未正常结束");
            fail.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 10.5f));
            fail.setTextColor(t.alpha(t.danger, 0.95f));
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            flp.topMargin = Ui.dpi(act, 6);
            card.addView(fail, flp);
        }

        String bodyText = !run.summary.isEmpty() ? run.summary
                : (run.turns.isEmpty() ? "" : run.turns.get(run.turns.size() - 1).text);
        if (!bodyText.trim().isEmpty()) {
            final TextView body = new TextView(act);
            String bodyShown = ChatPage.stripThink(bodyText);
            if (bodyShown.trim().isEmpty()) bodyShown = "(无正文)";
            try {
                body.setText(Markdown.render(act, bodyShown, t));
            } catch (Throwable e) {
                body.setText(bodyShown);
            }
            body.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 13));
            body.setLineSpacing(0, 1.35f);
            body.setTextColor(t.textPri);
            body.setMaxLines(expanded ? 80 : 5);
            body.setEllipsize(expanded ? null : android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.topMargin = Ui.dpi(act, 8);
            card.addView(body, blp);

            final TextView toggle = new TextView(act);
            toggle.setText(expanded ? "收起" : "展开全文");
            toggle.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sp(act, 11));
            toggle.setTextColor(t.alpha(t.accent, 0.95f));
            LinearLayout.LayoutParams tgl2 = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tgl2.topMargin = Ui.dpi(act, 6);
            toggle.setOnClickListener(v -> {
                boolean now = !expandedCards.contains(key);
                if (now) expandedCards.add(key); else expandedCards.remove(key);
                body.setMaxLines(now ? 80 : 5);
                body.setEllipsize(now ? null : android.text.TextUtils.TruncateAt.END);
                toggle.setText(now ? "收起" : "展开全文");
            });
            card.addView(toggle, tgl2);
        }

        wrap.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.setOnLongClickListener(vv -> {
            cp.dialogs.msgMenu(m, true);
            return true;
        });
        return wrap;
    }

    /** 思考折叠条：收起为一行摘要，点击展开/收起全文（半透明小字号） */
    void applyThinkBlock(TextView think, String key, String thinkText) {
        boolean expanded = expandedCards.contains(key);
        Icon.unpin(think);
        if (expanded) {
            think.setText("已深度思考\n" + thinkText.trim());
            think.setMaxLines(500);
        } else {
            think.setText("已深度思考");
            think.setMaxLines(1);
        }
        think.setVisibility(View.VISIBLE);
        Icon.pinLeft(think, "think", 13);
        think.setOnClickListener(v -> {
            if (expandedCards.contains(key)) expandedCards.remove(key);
            else expandedCards.add(key);
            applyThinkBlock(think, key, thinkText);
        });
    }
}
