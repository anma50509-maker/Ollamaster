package com.ollamaster;

import android.app.Dialog;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * 记忆库页面：抽屉式嵌套浏览。
 * - 分类路径（如 工作/项目/后端）形成多级「抽屉」，点击头部展开/收起
 * - 每条记忆卡片可展开查看全文，支持编辑 / 删除 / 复制
 * - 顶部搜索框按关键词过滤
 */
public class MemoryPage extends Page {

    private Theme t;
    private LinearLayout body;
    private EditText searchBox;
    private TextView statLine;
    private final SimpleDateFormat DF = new SimpleDateFormat("MM-dd HH:mm", Locale.US);
    private String q = "";

    public MemoryPage(MainActivity a) { super(a); }

    @Override
    protected View build() {
        t = Theme.of(act);
        ScrollView scroll = new ScrollView(act);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(t.bg);

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dpi(act, 16), Ui.dpi(act, 12), Ui.dpi(act, 16), Ui.dpi(act, 28));
        scroll.addView(col);

        // 标题行：标题 + 新增按钮
        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.title(act, t, "记忆库");
        Icon.pinLeft(title, "folder", 16);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView addBtn = Ui.btnPrimary(act, t, "新增");
        Icon.pinLeft(addBtn, "plus", 13);
        addBtn.setOnClickListener(v -> editDialog(null));
        head.addView(addBtn);
        col.addView(head);

        // 统计行
        statLine = Ui.caption(act, t, "");
        statLine.setPadding(0, Ui.dpi(act, 3), 0, Ui.dpi(act, 8));
        col.addView(statLine);

        // 搜索框
        searchBox = Ui.input(act, t, "搜索记忆内容…", false);
        searchBox.setCompoundDrawablesWithIntrinsicBounds(
                Icon.v(act, "search", t.alpha(t.textSec, 0.55f), 14), null, null, null);
        searchBox.setCompoundDrawablePadding(Ui.dpi(act, 7));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dpi(act, 42));
        searchBox.setOnEditorActionListener((v, actionId, ev) -> { refresh(); return true; });
        col.addView(searchBox, slp);

        col.addView(Ui.gap(act, 12));

        body = new LinearLayout(act);
        body.setOrientation(LinearLayout.VERTICAL);
        col.addView(body);

        return scroll;
    }

    @Override
    public void onShow() { refresh(); }

    private void refresh() {
        q = searchBox != null && searchBox.getText() != null
                ? searchBox.getText().toString().trim().toLowerCase(Locale.US) : "";
        buildBody(MemoryStore.list(act));
    }

    private void buildBody(List<MemoryStore.Item> all) {
        body.removeAllViews();
        List<String> cats = MemoryStore.categories(act, all);
        statLine.setText("共 " + all.size() + " 条记忆 · " + cats.size() + " 个分类抽屉");

        if (all.isEmpty()) {
            TextView empty = new TextView(act);
            empty.setText("记忆库还是空的\n\n点击上方「＋ 新增」手动保存，\n或让 AI 用 mem_write 工具写入第一条记忆。");
            empty.setTextColor(t.textSec);
            empty.setGravity(Gravity.CENTER);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.spi(act, 13));
            empty.setPadding(0, Ui.dpi(act, 80), 0, 0);
            body.addView(empty);
            return;
        }

        // 搜索模式：扁平展示命中结果
        if (!q.isEmpty()) {
            List<MemoryStore.Item> hits = new ArrayList<>();
            for (MemoryStore.Item it : all) {
                String hay = (it.title + " " + it.content + " " + it.tags + " " + it.category)
                        .toLowerCase(Locale.US);
                if (hay.contains(q)) hits.add(it);
            }
            if (hits.isEmpty()) {
                TextView none = new TextView(act);
                none.setText("未找到包含「" + q + "」的记忆");
                none.setTextColor(t.textSec);
                none.setGravity(Gravity.CENTER);
                none.setPadding(0, Ui.dpi(act, 50), 0, 0);
                body.addView(none);
                return;
            }
            drawer("搜索结果（" + hits.size() + "）", hits, true);
            return;
        }

        // 抽屉模式：全部 + 按一级分类分组
        drawer("全部记忆（" + all.size() + "）", all, true);
        if (!cats.isEmpty()) {
            LinkedHashMap<String, List<MemoryStore.Item>> groups = new LinkedHashMap<>();
            for (MemoryStore.Item it : all) {
                String cat = it.category == null ? "" : it.category.trim();
                String main = cat.isEmpty() ? "未分类" : cat.split("/")[0].trim();
                if (!groups.containsKey(main)) groups.put(main, new ArrayList<>());
                groups.get(main).add(it);
            }
            for (String group : new ArrayList<>(groups.keySet())) {
                List<MemoryStore.Item> items = groups.get(group);
                String label = "抽屉·" + group + "（" + items.size() + "）";
                drawer(label, items, false);
            }
        }
    }

    /** 一个可展开/收起的「抽屉」卡片 */
    private void drawer(String label, final List<MemoryStore.Item> items, final boolean defOpen) {
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.stroke(t.surface, t.border, Ui.dpi(act, 14), Ui.dpi(act, 0.7f)));
        card.setPadding(Ui.dpi(act, 13), Ui.dpi(act, 4), Ui.dpi(act, 13), Ui.dpi(act, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dpi(act, 10);
        body.addView(card, lp);

        final TextView header = new TextView(act);
        header.setText(label);
        header.setTextColor(t.textPri);
        header.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.spi(act, 13.5f));
        header.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.setPadding(0, Ui.dpi(act, 8), 0, Ui.dpi(act, 8));
        Icon.pinLeft(header, defOpen ? "chevronDown" : "chevronRight", 13);
        card.addView(header);

        final LinearLayout drawerBox = new LinearLayout(act);
        drawerBox.setOrientation(LinearLayout.VERTICAL);
        drawerBox.setVisibility(defOpen ? View.VISIBLE : View.GONE);
        card.addView(drawerBox);

        header.setOnClickListener(v -> {
            boolean show = drawerBox.getVisibility() != View.VISIBLE;
            drawerBox.setVisibility(show ? View.VISIBLE : View.GONE);
            String base = label;
            int idx = base.indexOf(' ');
            if (idx > 0) base = base.substring(idx + 1);
            header.setText(base);
            header.setCompoundDrawables(null, null, null, null);
            Icon.pinLeft(header, show ? "chevronDown" : "chevronRight", 13);
        });

        for (MemoryStore.Item it : items) {
            View cardView = itemCard(it);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ilp.topMargin = Ui.dpi(act, 6);
            drawerBox.addView(cardView, ilp);
        }
    }

    /** 单条记忆卡片：点击抽屉展开全文 + 操作栏 */
    private View itemCard(final MemoryStore.Item it) {
        final LinearLayout item = new LinearLayout(act);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setBackground(Ui.ripple(Ui.round(t.surfaceAlt, Ui.dpi(act, 10)), t.alpha(t.textPri, 0.1f)));
        item.setPadding(Ui.dpi(act, 12), Ui.dpi(act, 9), Ui.dpi(act, 12), Ui.dpi(act, 5));

        // 标题行（点击展开/收起）
        final LinearLayout topRow = new LinearLayout(act);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView arrow = new TextView(act);
        arrow.setText("");
        arrow.setTextColor(t.accent);
        Icon.pinCenter(arrow, "chevronRight", 13);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(Ui.dpi(act, 22), ViewGroup.LayoutParams.WRAP_CONTENT);
        topRow.addView(arrow, alp);

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(act);
        title.setText(it.title);
        title.setTextColor(t.textPri);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.spi(act, 14));
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setMaxLines(2);
        col.addView(title);

        StringBuilder meta = new StringBuilder();
        if (it.category != null && !it.category.isEmpty()) meta.append(it.category).append(" · ");
        if (it.tags != null && !it.tags.isEmpty()) meta.append(it.tags).append(" · ");
        meta.append(DF.format(new Date(it.updated)));
        TextView metaV = Ui.caption(act, t, meta.toString());
        metaV.setPadding(0, Ui.dpi(act, 2), 0, 0);
        col.addView(metaV);

        topRow.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        item.addView(topRow);

        // 内容抽屉
        final LinearLayout contentBox = new LinearLayout(act);
        contentBox.setOrientation(LinearLayout.VERTICAL);
        contentBox.setVisibility(View.GONE);
        TextView content = new TextView(act);
        content.setText(it.content == null || it.content.isEmpty() ? "（无内容）" : it.content);
        content.setTextColor(t.textSec);
        content.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.spi(act, 13));
        content.setLineSpacing(Ui.dpi(act, 3), 1f);
        content.setPadding(0, Ui.dpi(act, 8), 0, Ui.dpi(act, 4));
        contentBox.addView(content);
        item.addView(contentBox);

        // 操作栏
        final LinearLayout opsRow = new LinearLayout(act);
        opsRow.setOrientation(LinearLayout.HORIZONTAL);
        opsRow.setGravity(Gravity.END);
        opsRow.setVisibility(View.GONE);
        opsRow.setPadding(0, Ui.dpi(act, 2), 0, Ui.dpi(act, 4));

        opsRow.addView(miniBtn("复制", v -> {
            Ui.copy(act, "【" + it.title + "】" + (it.content == null ? "" : "\n" + it.content));
            Ui.toast(act, "已复制到剪贴板");
        }));
        opsRow.addView(miniBtn("编辑", v -> editDialog(it)));
        opsRow.addView(miniBtn("删除", v -> confirmDelete(it)));

        item.addView(opsRow);

        // 展开/收起全文（监听器需在 contentBox/opsRow 声明之后）
        topRow.setOnClickListener(v -> {
            boolean show = contentBox.getVisibility() != View.VISIBLE;
            contentBox.setVisibility(show ? View.VISIBLE : View.GONE);
            opsRow.setVisibility(show ? View.VISIBLE : View.GONE);
        });
        return item;
    }

    private TextView miniBtn(String text, View.OnClickListener l) {
        TextView b = new TextView(act);
        b.setText(text);
        b.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.spi(act, 12));
        b.setTextColor(t.accent);
        b.setPadding(Ui.dpi(act, 12), Ui.dpi(act, 5), Ui.dpi(act, 12), Ui.dpi(act, 5));
        b.setBackground(Ui.ripple(Ui.round(t.alpha(t.accent, 0.08f), Ui.dpi(act, 8)),
                t.alpha(t.textPri, 0.15f)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dpi(act, 6);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    /** 新增 / 编辑 弹窗（底部 sheet） */
    private void editDialog(final MemoryStore.Item old) {
        final boolean isNew = old == null;
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView et = Ui.title(act, t, isNew ? "新增记忆" : "编辑记忆");
        Icon.pinLeft(et, isNew ? "plus" : "edit", 15);
        col.addView(et);

        EditText t1 = Ui.input(act, t, "标题（必填，简洁概括）", false);
        if (old != null) t1.setText(old.title);
        col.addView(t1);
        col.addView(Ui.gap(act, 8));

        EditText t2 = Ui.input(act, t, "分类（如：工作/项目/后端，斜杠分层=嵌套抽屉）", false);
        if (old != null) t2.setText(old.category);
        col.addView(t2);
        col.addView(Ui.gap(act, 8));

        EditText t3 = Ui.input(act, t, "内容（完整记录）", true);
        if (old != null) t3.setText(old.content);
        col.addView(t3);
        col.addView(Ui.gap(act, 8));

        EditText t4 = Ui.input(act, t, "标签（逗号分隔，可选）", false);
        if (old != null) t4.setText(old.tags);
        col.addView(t4);
        col.addView(Ui.gap(act, 14));

        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = Ui.btnGhost(act, t, "取消");
        cancel.setOnClickListener(v2 -> {});
        btns.addView(cancel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView save = Ui.btnPrimary(act, t, "保存");
        btns.addView(save, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(btns);

        final Dialog d = Ui.sheet(act, col, t);
        d.show();
        cancel.setOnClickListener(v2 -> d.dismiss());
        save.setOnClickListener(v2 -> {
            String title = t1.getText() == null ? "" : t1.getText().toString().trim();
            if (title.isEmpty()) { Ui.toast(act, "标题不能为空"); return; }
            List<MemoryStore.Item> items = MemoryStore.list(act);
            MemoryStore.Item it;
            if (isNew) {
                it = new MemoryStore.Item();
                it.id = ConvStore.newId();
                items.add(it);
            } else {
                it = MemoryStore.find(items, old.id);
                if (it == null) { d.dismiss(); refresh(); return; }
            }
            it.title = title;
            it.category = (t2.getText() == null ? "" : t2.getText().toString()).trim();
            it.content = t3.getText() == null ? "" : t3.getText().toString();
            it.tags = (t4.getText() == null ? "" : t4.getText().toString()).trim();
            it.updated = System.currentTimeMillis();
            MemoryStore.saveAll(act, items);
            Ui.toast(act, isNew ? "记忆已保存" : "记忆已更新");
            d.dismiss();
            refresh();
        });
    }

    private void confirmDelete(final MemoryStore.Item it) {
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView dt = Ui.title(act, t, "删除记忆");
        Icon.pinLeft(dt, "trash", 14);
        col.addView(dt);
        TextView msg = Ui.caption(act, t, "确定删除「" + it.title + "」吗？此操作不可撤销。");
        msg.setPadding(0, Ui.dpi(act, 8), 0, Ui.dpi(act, 18));
        msg.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.spi(act, 13.5f));
        col.addView(msg);

        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = Ui.btnGhost(act, t, "取消");
        TextView del = Ui.btnPrimary(act, t, "删除");
        del.setTextColor(0xFFFFFFFF);
        btns.addView(cancel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btns.addView(del, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(btns);

        final Dialog d = Ui.center(act, col, t);
        d.show();
        cancel.setOnClickListener(v -> d.dismiss());
        del.setOnClickListener(v -> {
            MemoryStore.remove(it.id);
            Ui.toast(act, "已删除: " + it.title);
            d.dismiss();
            refresh();
        });
    }
}