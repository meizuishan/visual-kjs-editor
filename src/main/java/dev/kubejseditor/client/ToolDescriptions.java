package dev.kubejseditor.client;

/** Shared help for the five document tools. */
final class ToolDescriptions {
    private static final String[] TEXT={
        "打开可移动的背包取样窗。拖动标题移动，拖动物品到配方槽位；单击物品可选目标。只复制，不消耗物品。",
        "列出这份配方 JSON 内的数值，包括尚未自动识别的嵌套字段。与可视化参数修改同一份数据；字段含义由原模组决定。",
        "按真实 JSON 路径修改、添加或删除任意字段，适合未自动识别的对象、数组、条件和组件。保存前检查原生格式。",
        "同一份文档的原始 JSON、生成代码和修改差异。代码与差异为只读预览，使用尚未保存的 JSON。",
        "点击切换替换或新建。替换保留来源 ID，新建使用独立 ID；当前编辑内容保持不变。"
    };
    static String at(int index){return TEXT[index];}
    private ToolDescriptions(){}
}
