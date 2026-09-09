import type { ThemeConfig } from "ant-design-vue/es/config-provider/context";

export const appTheme: ThemeConfig = {
  token: {
    colorPrimary: "#4D6BFE",
    colorInfo: "#4D6BFE",
    colorSuccess: "#1a7f37",
    colorWarning: "#9a6700",
    colorError: "#d1242f",
    colorLink: "#4D6BFE",
    colorText: "#1f2328",
    colorTextSecondary: "#59636e",
    colorBorder: "#d0d7de",
    colorBgLayout: "#ffffff",
    colorBgContainer: "#ffffff",
    borderRadius: 8,
    fontFamily:
      '-apple-system, BlinkMacSystemFont, "Segoe UI", "Noto Sans SC", "PingFang SC", "Microsoft YaHei", Helvetica, Arial, sans-serif',
    fontSize: 14,
    controlHeight: 32,
  },
  components: {
    Layout: {
      headerBg: "#ffffff",
      bodyBg: "#ffffff",
      siderBg: "#f7f8fc",
    },
    Table: {
      headerBg: "#f6f8fa",
      headerColor: "#1f2328",
      borderColor: "#d0d7de",
      cellPaddingBlock: 8,
      cellPaddingInline: 12,
    },
    Tabs: {
      itemActiveColor: "#1f2328",
      itemSelectedColor: "#1f2328",
      inkBarColor: "#4D6BFE",
      itemColor: "#1f2328",
      titleFontSize: 14,
    },
    Button: {
      primaryShadow: "none",
    },
  },
};
