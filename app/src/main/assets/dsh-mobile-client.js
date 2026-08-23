(function() { "use strict";
if (typeof window === "undefined") return;
if (!window.__ModuleLoader__) window.__ModuleLoader__ = { load: function() {} };

window.__ModuleLoader__.load({
  id: "dsh-web-ui-mobile",
  factory: function(require) {
    var MOBILE_READY = false;
    var DRILL_BOUND = false;
    var AUTO_CLOSE_BOUND = false;
    var APP_SECTION_BOUND = false;

    function injectCss() {
      if (document.getElementById("dsh-mobile-css")) return;
      var el = document.createElement("style");
      el.id = "dsh-mobile-css";
      el.textContent = [
        /* 侧边栏 off-canvas 隐藏 */
        ".dsh-mobile-active .hHd-Xa_root{",
          "position:fixed !important;top:0 !important;left:0 !important;",
          "height:100vh !important;height:100dvh !important;",
          "z-index:9999 !important;transform:translateX(-100%) !important;",
          "transition:transform 0.3s cubic-bezier(0.4,0,0.2,1),width 0.3s ease !important;",
          "box-shadow:none !important;overflow:hidden !important;",
          "display:flex !important;flex-direction:column !important;",
        "}",
        /* 抽屉打开：全屏宽度 + 主题配色。
           可见性寄生在 App 自己的 hHd-Xa_collapsed 类上：无此类=展开=抽屉打开。
           该类由 App 维护，重渲染/切会话都不会丢，天然消除竞态。 */
        ".dsh-mobile-active .hHd-Xa_root:not(.hHd-Xa_collapsed){",
          "transform:translateX(0) !important;width:100vw !important;max-width:100vw !important;",
          "box-shadow:2px 0 12px rgba(0,0,0,0.2) !important;",
          "background:var(--dsw-alias-bg-base,#fff) !important;color:var(--dsw-alias-label-primary,#0f1115) !important;",
        "}",
        /* 隐藏应用自带的 rail toggle 按钮 */
        /* toggle \u6309\u94ae\u4fdd\u7559\uff0c\u539f\u751f\u6253\u5f00\u4fa7\u680f */
        ".dsh-mobile-active .hHd-Xa_root .hHd-Xa_logoRow{",
          "display:flex !important;width:100% !important;justify-content:flex-end !important;",
          "padding:8px !important;box-sizing:border-box !important;",
        "}",
        /* newSession 按钮展开 */
        ".dsh-mobile-active .hHd-Xa_root:not(.hHd-Xa_collapsed) .hHd-Xa_newSession{",
          "width:100% !important;display:flex !important;align-items:center !important;",
          "justify-content:center !important;padding:12px 16px !important;",
          "margin:4px 8px !important;box-sizing:border-box !important;",
        "}",
        /* 入口按钮展开 + 显示文字标签 */
        ".dsh-mobile-active .hHd-Xa_root:not(.hHd-Xa_collapsed) [class*=entry]{",
          "width:100% !important;padding:12px 16px !important;",
          "flex-direction:row !important;gap:12px !important;",
          "justify-content:flex-start !important;align-items:center !important;",
          "border-radius:8px !important;margin:2px 0 !important;box-sizing:border-box !important;",
          "text-align:left !important;",
        "}",
        ".dsh-mobile-active .hHd-Xa_root:not(.hHd-Xa_collapsed) [class*=entryLabel]{",
          "display:inline !important;width:auto !important;height:auto !important;",
          "overflow:visible !important;white-space:nowrap !important;font-size:14px !important;",
          "text-align:left !important;line-height:1.4 !important;",
        "}",
        ".dsh-mobile-active .hHd-Xa_root:not(.hHd-Xa_collapsed) [class*=entryIcon]{",
          "width:20px !important;height:20px !important;flex-shrink:0 !important;",
          "display:flex !important;align-items:center !important;justify-content:center !important;",
        "}",
        /* footer 区域展开 */
        ".dsh-mobile-active .hHd-Xa_root:not(.hHd-Xa_collapsed) .hHd-Xa_footArea{",
          "width:100% !important;flex-direction:column !important;flex-wrap:nowrap !important;",
          "justify-content:flex-start !important;padding:8px 16px !important;box-sizing:border-box !important;",
        "}",
        ".dsh-mobile-active .hHd-Xa_root:not(.hHd-Xa_collapsed) .hHd-Xa_footerActions{",
          "width:100% !important;flex-direction:column !important;flex-wrap:nowrap !important;",
          "gap:0 !important;",
        "}",
        ".dsh-mobile-active .hHd-Xa_root:not(.hHd-Xa_collapsed) .hHd-Xa_footerActions > *{",
          "width:100% !important;flex:0 0 auto !important;padding:10px 12px !important;",
          "display:flex !important;align-items:center !important;gap:10px !important;",
          "border-radius:8px !important;min-height:40px !important;",
        "}",
        ".dsh-mobile-active .hHd-Xa_root:not(.hHd-Xa_collapsed) .hHd-Xa_settingsArea{",
          "width:100% !important;flex-direction:column !important;",
          "justify-content:flex-start !important;padding:10px 12px !important;",
          "gap:0 !important;margin-top:8px !important;border-top:none !important;",
        "}",
        ".dsh-mobile-active .hHd-Xa_root:not(.hHd-Xa_collapsed) .hHd-Xa_settingsArea > *{",
          "width:100% !important;padding:10px 12px !important;",
          "display:flex !important;align-items:center !important;gap:10px !important;",
          "min-height:40px !important;border-radius:8px !important;",
        "}",
        /* 搜索框：展开为可见输入框（官方窄屏折叠成 4px 图标） */
        /* 搜索：官方窄屏会把输入框折叠成图标(!important)，实测 input 仍以
           140px 可见可点（溢出展示），不再与其宽度规则缠斗。 */
        ".dsh-mobile-active .qDHVXG_sectionHeader{flex-wrap:wrap !important;}",
        ".dsh-mobile-active .qDHVXG_search input{",
          "width:100% !important;min-width:140px !important;height:32px !important;display:block !important;",
        "}",
        /* regionArea 展开：撑满中部，footer(设置)贴底 */
        ".dsh-mobile-active .hHd-Xa_root:not(.hHd-Xa_collapsed) .hHd-Xa_regionArea{",
          "width:100% !important;flex:1 1 auto !important;min-height:0 !important;",
          "overflow-y:auto !important;-webkit-overflow-scrolling:touch !important;",
          "padding:0 8px !important;box-sizing:border-box !important;",
        "}",
        /* 设置入口固定在可视区底边上方（安全区 - 2px，即约再低 30px）：
           有安全区的机型由 env 兜底，不会贴进手势条 */
        /* 设置区放到顶部（logo 边），会话/工作区列表占据其余空间 */
        /* logo 行下探避开状态栏（沉浸式：内容延伸到状态栏底下） */
        ".dsh-mobile-active .hHd-Xa_root:not(.hHd-Xa_collapsed) .hHd-Xa_logoRow{",
          "padding-top:10px !important;",
        "}",

        /* 设置区保持可见、可点：作为抽屉底部的正常入口 */
        /* 折叠态：完全隐藏 rail（0宽度） */
        ".dsh-mobile-active .hHd-Xa_root.hHd-Xa_collapsed{",
          "width:0 !important;min-width:0 !important;padding:0 !important;margin:0 !important;",
          "overflow:hidden !important;",
        "}",
        /* 官方框架的侧栏容器列也要归零，否则收起后残留一条白色空列，
           聊天窗口无法全屏（:has 现代移动浏览器均支持）。 */
        ".dsh-mobile-active .pI_x6G_sidebarCol:has(.hHd-Xa_collapsed){",
          "width:0 !important;min-width:0 !important;flex:0 0 0px !important;",
          "border:none !important;background:transparent !important;overflow:hidden !important;",
        "}",
        /* App 会用 JS 写死 grid 轨道（如 56px 折叠条），强制中列吃满：
           抽屉是 fixed 定位浮层，不依赖网格轨道。 */
        ".dsh-mobile-active .pI_x6G_frame{",
          "grid-template-columns:0px 1fr 0px !important;",
        "}",
        /* 统一 content-box→border-box：强制 100% 宽 + padding 的子元素不再横向溢出 */
        ".dsh-mobile-active .hHd-Xa_root,",
        ".dsh-mobile-active .hHd-Xa_root *{box-sizing:border-box !important;}",
        /* ===== 模型选择菜单：固定为全宽选单层（React 重渲染免疫） =====
           官方绝对定位以芯片右缘向左展开，窄屏芯片靠左时整体跑出屏幕；
           改为 fixed 全宽层，上下留出浏览器 UI 与输入框。 */
        ".dsh-mobile-active ._7KE1Ra_menu[role=\"menu\"]{",
          "position:fixed !important;left:8px !important;right:8px !important;",
          "top:auto !important;bottom:76px !important;height:352px !important;max-height:60dvh !important;width:auto !important;max-width:none !important;",
          "overflow-y:auto !important;",
        "}",
        /* 滚动统一由菜单容器承担，内层不再各自为政 */
        ".dsh-mobile-active ._7KE1Ra_groups{",
          "max-height:none !important;overflow:visible !important;",
        "}",
          "background:var(--dsw-alias-bg-base,#fff) !important;color:var(--dsw-alias-label-primary,#0f1115) !important;",
          "border-radius:14px !important;border:1px solid var(--dsw-alias-border-l2,rgba(127,127,127,0.25)) !important;",
          "box-shadow:0 12px 32px rgba(0,0,0,0.18) !important;",
          "-webkit-overflow-scrolling:touch !important;",
        "}",
        /* 分组标题：吸附顶部的小字标签 */
        ".dsh-mobile-active ._7KE1Ra_groupTitle{",
          "position:sticky !important;top:0 !important;z-index:1 !important;",
          "background:var(--dsw-alias-bg-base,#fff) !important;",
          "font-size:12px !important;letter-spacing:0.02em !important;",
          "color:var(--dsw-alias-label-tertiary,#8a8f98) !important;",
        "}",
        /* 模型行：44px 触摸目标 + 圆角 + 按压反馈 */
        ".dsh-mobile-active ._7KE1Ra_option{",
          "min-height:44px !important;padding:10px 14px !important;",
          "border-radius:10px !important;",
        "}",
        ".dsh-mobile-active ._7KE1Ra_option:hover,",
        ".dsh-mobile-active ._7KE1Ra_option:active{",
          "background:var(--dsw-alias-interactive-bg-hover,rgba(127,127,127,0.15)) !important;",
        "}",
        /* 设置弹窗：外观三卡缩为一排 */
        ".dsh-mobile-active ._8HJdBW_cubeRow{flex-wrap:nowrap !important;gap:8px !important;}",
        ".dsh-mobile-active ._8HJdBW_themeCube{",
          "flex:1 1 0 !important;width:auto !important;min-width:0 !important;max-width:none !important;",
          "height:60px !important;padding:8px 4px !important;gap:4px !important;",
        "}",

        /* ===== 输入框工具行（composer）手机适配 ===== */
        /* 单行排列、垂直居中、高度统一 34px */
        ".dsh-mobile-active .uV2eYG_row{",
          "flex-wrap:nowrap !important;align-items:center !important;",
        "}",
        ".dsh-mobile-active .uV2eYG_trailing{",
          "flex:1 1 auto !important;min-width:0 !important;",
        "}",
        ".dsh-mobile-active .uV2eYG_add,",
        ".dsh-mobile-active .Sh0Q9G_trigger,",
        ".dsh-mobile-active ._7KE1Ra_trigger{height:34px !important;}",
        /* 模型芯片紧凑化：只显示前几个字母，超出省略号 */
        ".dsh-mobile-active ._7KE1Ra_root{",
          "flex:0 1 auto !important;width:auto !important;min-width:0 !important;max-width:190px !important;",
        "}",
        ".dsh-mobile-active ._7KE1Ra_trigger{width:auto !important;max-width:none !important;}",
        ".dsh-mobile-active ._7KE1Ra_cellValue,",
        ".dsh-mobile-active ._7KE1Ra_triggerLabel{",
          "display:block !important;overflow:hidden !important;",
          "text-overflow:ellipsis !important;white-space:nowrap !important;",
          "min-width:0 !important;max-width:96px !important;",
        "}",
        ".dsh-mobile-active ._7KE1Ra_cellLabel{white-space:nowrap !important;flex-shrink:0 !important;}",
        /* ===== 顶栏：☰ + DeepSeek Harness（实心底色，与页面融为一体） ===== */
                /* ===== 设置弹窗（VOzbGW）手机适配 ===== */
        /* 面板全屏、纵向布局：顶部横向导航 + 内容独占整行 */
        ".dsh-mobile-active .VOzbGW_overlay{padding:0 !important;}",
        ".dsh-mobile-active .VOzbGW_panel{",
          "width:100vw !important;height:100dvh !important;max-height:100dvh !important;",
          "max-width:100vw !important;",
          "left:0 !important;top:0 !important;border-radius:0 !important;",
          "flex-direction:column !important;",
        "}",
        /* 导航横排、可横滑 */
        ".dsh-mobile-active .VOzbGW_nav{",
          "flex-direction:row !important;align-items:center !important;",
          "width:100vw !important;height:auto !important;min-height:0 !important;flex:0 0 auto !important;",
          "overflow-x:auto !important;overflow-y:hidden !important;",
          "padding:6px 8px !important;box-sizing:border-box !important;",
          "border-bottom:1px solid var(--dsw-alias-border-l2,rgba(127,127,127,0.25)) !important;",
        "}",
        ".dsh-mobile-active .VOzbGW_nav > *{flex:0 0 auto !important;}",
        /* 冗余大标题隐藏；列表改横排 pill，导航压成一条 ~52px 的 tab 条 */
        ".dsh-mobile-active .VOzbGW_navTitle{display:none !important;}",
        ".dsh-mobile-active .VOzbGW_navList{",
          "flex-direction:row !important;align-items:center !important;",
          "width:auto !important;height:auto !important;gap:6px !important;",
          "overflow-x:auto !important;overflow-y:hidden !important;",
        "}",
        ".dsh-mobile-active .VOzbGW_navCell{",
          "white-space:nowrap !important;flex:0 0 auto !important;",
          "padding:8px 14px !important;height:38px !important;",
        "}",
        /* 内容列独占整行、可竖向滚动 */
        ".dsh-mobile-active .VOzbGW_content{",
          "flex:1 1 auto !important;width:100vw !important;min-width:0 !important;",
          "overflow-y:auto !important;overflow-x:hidden !important;",
          "-webkit-overflow-scrolling:touch !important;",
        "}",
        /* body 行为 */
        "body.dsh-mobile-active{font-size:15px !important;}",
        ".dsh-mobile-active *{-webkit-tap-highlight-color:rgba(0,0,0,0.05);}",
        "@media screen and (max-width:768px){",
          ".dsh-mobile-active [class*=message]{max-width:100% !important;}",
        "}",
        /* ===== OpenClaw \u98ce\u683c\u8c03\u8272\u677f ===== */
        /* \u4f1a\u8bdd\u884c\u6837\u5f0f\uff1a\u72b6\u6001\u70b9 + \u4e24\u884c\u6587\u672c + \u5706\u89d2\u9009\u4e2d\u9ad8\u4eae */
        ".dsh-mobile-active [class*=sessionRow]{",
          "min-height:48px !important;border-radius:10px !important;",
          "padding:8px 12px !important;margin:1px 0 !important;",
        "}",
        ".dsh-mobile-active [class*=sessionRow][class*=selected],",
        ".dsh-mobile-active [class*=sessionRow][aria-current]{",
          "background:#EDEDED !important;",
        "}",
        "body.dsh-app-dialog-open .hHd-Xa_root:not(.hHd-Xa_collapsed) .hHd-Xa_regionArea{visibility:hidden !important;}",
        /* \u6df1\u8272\u6a21\u5f0f\u9002\u914d */
        "@media (prefers-color-scheme: dark){",
          ".dsh-mobile-active .hHd-Xa_root:not(.hHd-Xa_collapsed){",
            "background:#000 !important;color:#EDEDED !important;",
          "}",
          ".dsh-mobile-active [class*=sessionRow][class*=selected],",
          ".dsh-mobile-active [class*=sessionRow][aria-current]{",
            "background:#232327 !important;",
          "}",
        "}",
      ].join("");
      document.head.appendChild(el);
    }

    /* 始终实时解析当前侧边栏节点：App 切换会话/重渲染时会替换 rail 元素，
       任何闭包持有旧节点都会导致按钮失灵。 */
    function findSidebar() {
      return document.querySelector(".hHd-Xa_root");
    }

    function drawerOpen(sb) {
      /* 抽屉开=App 处于展开态（无 collapsed 类）。状态归 App 所有，永不失同步。 */
      return !!sb && !sb.classList.contains("hHd-Xa_collapsed");
    }

    function syncOverlay(open) {
      var ov = document.getElementById("dsh-mobile-ov");
      if (ov) ov.style.display = open ? "block" : "none";
      try { if (window.DshAppBridge) window.DshAppBridge.setSidebarOpen(!!open); } catch (e) {}
    }



    /* 打开/关闭抽屉。sb 必须是调用方实时解析的当前节点。
       打开/关闭都通过点击 App 官方 toggle 翻转 hHd-Xa_collapsed：
       - 窄屏折叠态下列表/标签根本不渲染，必须让 App 走展开渲染；
       - 类由 App 维护，重渲染、切会话都不会丢，天然消除竞态。 */
    function doToggle(sb) {
      if (!sb) return;
      var opening = sb.classList.contains("hHd-Xa_collapsed");
      var tgl = sb.querySelector('.hHd-Xa_toggle');
      if (!tgl) return;
      try { tgl.click(); } catch(e) {}
      if (opening) {
        setTimeout(function() {
          var cur = findSidebar();
          if (cur && drawerOpen(cur)) {
            applyDrawerStyles(cur);
            /* 自动展开官方搜索（窄屏默认折叠成图标） */
            var sbtn = cur.querySelector('.qDHVXG_searchButton');
            if (sbtn && sbtn.getAttribute('aria-expanded') === 'false') { try { sbtn.click(); } catch(e) {} }
          }
        }, 60);
      } else {
        clearDrawerStyles(sb);
      }
      syncOverlay(opening);
    }

    /* 尝试通过 cordis 服务关闭应用自带侧边栏 rail */
    function tryCollapseAppSidebar() {
      try {
        // 方法 1：通过 CSS 变量强制宽度为 0
        document.documentElement.style.setProperty("--dsh-sidebar-width", "0px");
        // 方法 2：如果能访问 cordis 上下文（通过全局 app）
        if (window.__app__ && window.__app__.service && window.__app__.service.layout) {
          window.__app__.service.layout.setSidebar(0);
        }
      } catch(e) {}
    }

    /* 打开抽屉时：用内联样式强制修正布局（比 CSS !important 更可靠） */
    function applyDrawerStyles(sb) {
      /* 侧边栏整体；配色跟随应用主题（--dsw-alias-* 由 body 提供，浅色/深色自动切换） */
      sb.style.cssText = "position:fixed !important;top:0 !important;left:0 !important;width:100vw !important;z-index:9999 !important;transform:translateX(0) !important;transition:transform 0.3s cubic-bezier(0.4,0,0.2,1) !important;box-shadow:2px 0 12px rgba(0,0,0,0.2) !important;display:flex !important;flex-direction:column !important;overflow:hidden !important;background:var(--dsw-alias-bg-base,#fff) !important;color:var(--dsw-alias-label-primary,#0f1115) !important;";
      /* 官方 logoRow 直接优化：安全区留白 + 发丝分隔线，内容保持网页原样 */
      var logoRow = sb.querySelector('.hHd-Xa_logoRow');
      if (logoRow) {
        logoRow.style.cssText = "display:flex !important;width:100% !important;align-items:center !important;justify-content:flex-end !important;padding:calc(10px + env(safe-area-inset-top)) 14px 10px 12px !important;box-sizing:border-box !important;flex-shrink:0 !important;";
        var toggleBtn = logoRow.querySelector('.hHd-Xa_toggle');
        if (toggleBtn && !logoRow.querySelector('.dsh-side-gear')) {
          var gear = document.createElement('button');
          gear.type = 'button';
          gear.className = 'dsh-side-gear';
          gear.setAttribute('aria-label', '打开设置');
          gear.innerHTML = '&#9881;';
          gear.addEventListener('click', function(e) {
            e.stopPropagation();
            try { if (window.DshAppBridge) window.DshAppBridge.openAppSettings(); } catch (err) {}
          });
          logoRow.insertBefore(gear, toggleBtn);
        }
      }
      /* newSession 按钮展开 */
      var ns = sb.querySelector('.hHd-Xa_newSession');
      if (ns) {
        ns.style.cssText = "width:100% !important;display:flex !important;align-items:center !important;justify-content:center !important;padding:12px 16px !important;box-sizing:border-box !important;flex-shrink:0 !important;";
      }
      /* 入口按钮：图标+文字横排 */
      var entries = sb.querySelectorAll('[class$=_entry]');
      for (var i = 0; i < entries.length; i++) {
        var e = entries[i];
        if (e.classList.contains('hHd-Xa_newSession')) continue;
        e.style.cssText = "width:100% !important;display:flex !important;flex-direction:row !important;gap:12px !important;align-items:center !important;padding:10px 16px !important;box-sizing:border-box !important;border-radius:8px !important;margin:2px 0 !important;flex-shrink:0 !important;";
      }
      /* 图标容器 */
      var icons = sb.querySelectorAll('[class$=entryIcon]');
      for (var ii = 0; ii < icons.length; ii++) {
        icons[ii].style.cssText = "width:20px !important;height:20px !important;flex-shrink:0 !important;display:flex !important;align-items:center !important;justify-content:center !important;";
        var svg = icons[ii].querySelector('svg');
        if (svg) svg.style.cssText = "width:18px !important;height:18px !important;display:block !important;";
      }
      /* 文字标签 */
      var labels = sb.querySelectorAll('[class$=entryLabel]');
      for (var il = 0; il < labels.length; il++) {
        labels[il].style.cssText = "display:block !important;width:auto !important;height:auto !important;font-size:14px !important;line-height:1.4 !important;white-space:nowrap !important;overflow:visible !important;";
      }
      /* regionArea: 撑满中部，footer 贴底 */
      var region = sb.querySelector('.hHd-Xa_regionArea');
      if (region) region.style.cssText = "width:100% !important;flex:1 1 auto !important;min-height:0 !important;overflow-y:auto !important;-webkit-overflow-scrolling:touch !important;padding:0 8px !important;box-sizing:border-box !important;";
      /* 区域内部的 rail/list 容器 */
      var regionInner = sb.querySelector('[class*=sectionHeader]');
      if (regionInner) regionInner.style.cssText = "width:100% !important;display:flex !important;justify-content:space-between !important;align-items:center !important;padding:8px 12px !important;";
      var searchArea = sb.querySelector('[class*=search]');
      if (searchArea) searchArea.style.cssText = "width:100% !important;display:flex !important;align-items:center !important;padding:4px 12px !important;";
      var listArea = sb.querySelector('[class*=listArea]');
      if (listArea) listArea.style.cssText = "width:100% !important;overflow-y:auto !important;";
      /* footer */
      var foot = sb.querySelector('.hHd-Xa_footArea');
      if (foot) foot.style.cssText = "width:100% !important;flex-direction:column !important;padding:8px 16px !important;box-sizing:border-box !important;flex-shrink:0 !important;";
      var fActions = sb.querySelector('.hHd-Xa_footerActions');
      if (fActions) fActions.style.cssText = "width:100% !important;display:flex !important;flex-direction:row !important;flex-wrap:wrap !important;gap:8px !important;";
      var fItems = fActions ? fActions.children : [];
      for (var j = 0; j < fItems.length; j++) {
        fItems[j].style.cssText = "width:100% !important;display:flex !important;align-items:center !important;gap:10px !important;padding:10px 16px !important;border-radius:8px !important;min-height:44px !important;";
        var trigger = fItems[j].querySelector('button');
        if (trigger) trigger.style.cssText = "width:100% !important;display:flex !important;align-items:center !important;gap:10px !important;padding:10px 0 !important;background:none !important;border:none !important;color:inherit !important;cursor:pointer !important;font-size:14px !important;";
      }
      /* 设置入口只保留 App 原生设置页（头部 ⚙），隐藏网页自带设置区与底部操作区 */
      var settings = sb.querySelector('.hHd-Xa_settingsArea');
      if (settings) settings.style.display = "none";
      var footArea = sb.querySelector('.hHd-Xa_footArea');
      if (footArea) footArea.style.display = "none";
      var footerActions = sb.querySelector('.hHd-Xa_footerActions');
      if (footerActions) footerActions.style.display = "none";
    }

    function clearDrawerStyles(sb) {
      sb.style.cssText = "";
      var all = sb.querySelectorAll('*');
      for (var i = 0; i < all.length; i++) all[i].style.cssText = "";
    }

    function setupSidebar() {
      if (!document.getElementById("dsh-mobile-ov")) {
        var ov = document.createElement("div");
        ov.id = "dsh-mobile-ov";
        ov.style.cssText = "position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.5);z-index:9998;display:none;";
        ov.addEventListener("click", function() { doToggle(findSidebar()); });
        document.body.appendChild(ov);
      }
      /* 侧边栏入口由原生顶栏 ☰ 承担，不再创建悬浮按钮 */
    }

    /* 手机上跳过「Model →」中间层：菜单挂载后自动下钻到模型列表 */
    function bindModelMenuDrill() {
      if (DRILL_BOUND) return;
      DRILL_BOUND = true;
      document.addEventListener("click", function(e) {
        var t = e.target;
        if (!(t && t.closest)) return;
        if (!t.closest('._7KE1Ra_trigger')) return;
        var tries = 0;
        var iv = setInterval(function() {
          tries++;
          var m = document.querySelector('._7KE1Ra_menu[role="menu"]');
          if (!m) { if (tries > 25) clearInterval(iv); return; }
          var cell = m.querySelector('[role="menuitem"]');
          if (cell) { try { cell.click(); } catch(err) {} clearInterval(iv); return; }
          if (tries > 25) clearInterval(iv);
        }, 90);
      }, true);
    }

    /* 安卓 App 内（UA 含 DshAndroid/x）：在 设置→通用 底部注入 App 区块，
       经 DshAppBridge 调回原生（切换服务器/清除登录/许可证）。 */
    var APP_SECTION_PENDING = false;

    function injectAppSection() {
      if (!window.DshAppBridge) return;
      var content = document.querySelector(".VOzbGW_content");
      if (!content) return;
      var existing = content.querySelector(".dsh-app-section");
      var txt = content.textContent || "";
      var isGeneral = txt.indexOf("Language") >= 0 || txt.indexOf("语言") >= 0 ||
                      txt.indexOf("Appearance") >= 0 || txt.indexOf("外观") >= 0;
      if (!isGeneral) {
        if (existing) existing.remove();
        return;
      }
      if (existing) return;
      var ver = (navigator.userAgent.match(/DshAndroid\/([\d.]+)/) || [])[1] || "?";
      var sec = document.createElement("div");
      sec.className = "dsh-app-section";
      sec.style.cssText = "border-top:1px solid rgba(127,127,127,0.25);margin-top:10px;";
      var title = document.createElement("div");
      title.textContent = "App";
      title.style.cssText = "font-size:12px;color:#8a8f98;padding:6px 14px 2px;";
      sec.appendChild(title);
      function row(label, fn) {
        var r = document.createElement("div");
        r.textContent = label;
        r.style.cssText = "min-height:44px;display:flex;align-items:center;padding:8px 14px;" +
          "font-size:14px;color:var(--dsw-alias-label-primary,#111);cursor:pointer;border-radius:10px;";
        r.addEventListener("mouseenter", function(){ r.style.background = "rgba(127,127,127,0.12)"; });
        r.addEventListener("mouseleave", function(){ r.style.background = "transparent"; });
        var lock = false;
        function fire() {
          if (lock) return;
          lock = true;
          if (typeof fn === "function") fn();
          setTimeout(function() { lock = false; }, 350);
        }
        r.addEventListener("click", function(e) { e.preventDefault(); fire(); });
        r.addEventListener("touchend", function(e) { e.preventDefault(); fire(); }, { passive: false });
        sec.appendChild(r);
        return r;
      }
      var verRow = row("版本 v" + ver + "　·　检查更新");
      verRow.addEventListener("click", function() {
        verRow.textContent = "检查更新中…";
        fetch("https://api.github.com/repos/aqiyoung/deepseek-harness/releases/latest", { headers: { Accept: "application/vnd.github+json" } })
          .then(function(r2) { return r2.json(); })
          .then(function(j) {
            var tag = ((j && j.tag_name) || "").replace(/^v/, "");
            function cmp(a, b) {
              a = a.split("."); b = b.split(".");
              for (var i = 0; i < Math.max(a.length, b.length); i++) {
                var x = parseInt(a[i] || "0", 10), y = parseInt(b[i] || "0", 10);
                if (x !== y) return x - y;
              }
              return 0;
            }
            if (tag && cmp(tag, ver) > 0) {
              verRow.textContent = "新版本 v" + tag + " — 点击前往下载";
              verRow.style.color = "#0a7d38";
              verRow.onclick = function() { location.href = "https://github.com/aqiyoung/deepseek-harness/releases/latest"; };
            } else {
              verRow.textContent = "已是最新版本 ✓";
              setTimeout(function() { verRow.textContent = "版本 v" + ver + "　·　检查更新"; }, 1600);
            }
          })
          .catch(function() {
            verRow.textContent = "检查失败，点击重试";
            verRow.onclick = function() { verRow.click(); };
          });
      });
      row("切换服务器…", function() { if (window.DshAppBridge) window.DshAppBridge.changeServer(); });
      row("清除登录状态", function() { if (window.DshAppBridge) window.DshAppBridge.clearLogin(); });
      row("开源许可证", function() { if (window.DshAppBridge) window.DshAppBridge.showLicenses(); });
      content.appendChild(sec);
    }

    function bindAppSettingsSection() {
      if (!(/DshAndroid\/[\d.]+/.test(navigator.userAgent))) return;
      if (APP_SECTION_BOUND) return;
      APP_SECTION_BOUND = true;
      var pending = false;
      var observer = new MutationObserver(function() {
        if (pending) return;
        pending = true;
        requestAnimationFrame(function() {
          pending = false;
          try {
            injectAppSection();
            /* 设置弹窗打开期间隐藏整个抽屉，避免列表压在弹窗上拦截触摸 */
            var panelOpen = !!document.querySelector('.VOzbGW_panel');
            document.body.classList.toggle('dsh-app-dialog-open', panelOpen);
          } catch (e) {}
        });
      });
      observer.observe(document.body, { childList: true, subtree: true });
    }

    /* 抽屉打开时，点击会话行/新建会话后自动收起抽屉（跳转由 App 原生处理） */
    function bindDrawerAutoClose() {
      if (AUTO_CLOSE_BOUND) return;
      AUTO_CLOSE_BOUND = true;
      document.addEventListener("click", function(e) {
        var sb = findSidebar();
        if (!sb || !drawerOpen(sb)) return;
        var t = e.target;
        if (!(t && t.closest)) return;
        if (t.closest('[class*=sessionRow], .hHd-Xa_newSession')) {
          setTimeout(function() {
            var cur = findSidebar();
            if (cur && drawerOpen(cur)) doToggle(cur);
          }, 150);
        }
      }, true);
    }

    function setupTouch() {
      var lpT = null, sx = 0, sy = 0, st = 0;
      document.addEventListener("touchstart", function(e) {
        var t = e.touches[0]; sx = t.clientX; sy = t.clientY; st = Date.now();
        if (lpT) clearTimeout(lpT);
        lpT = setTimeout(function() { var el = document.elementFromPoint(sx, sy); if (el) el.dispatchEvent(new CustomEvent("longpress", { bubbles: true })); }, 800);
      }, { passive: true });
      document.addEventListener("touchmove", function() { if (lpT) { clearTimeout(lpT); lpT = null; } }, { passive: true });
      document.addEventListener("touchend", function(e) {
        var t = e.target; if (!t) return;
        if (lpT) { clearTimeout(lpT); lpT = null; }
        /* 不再合成 t.click()：viewport 正常时现代移动浏览器没有 300ms 点击延迟，
           原生 click 本就会触发；额外的合成 click 会让同一次点按触发两次，
           把刚打开的弹层/菜单立刻关掉（模型选择、权限模式、设置入口等）。 */
        var dx = e.changedTouches[0].clientX - sx;
        var dy = e.changedTouches[0].clientY - sy;
        var sb = findSidebar();
        if (!sb) return;
        var isOpen = drawerOpen(sb);
        if (Math.abs(dx) > 80 && Math.abs(dx) > Math.abs(dy) && Date.now() - st < 500) {
          if ((dx > 0 && !isOpen) || (dx < 0 && isOpen)) doToggle(sb);
        }
      }, { passive: true });
      window.addEventListener("resize", function() {
        var sb = findSidebar();
        if (sb && drawerOpen(sb) && window.innerWidth > 818) doToggle(sb);
      });
    }

    /* 会话点选后的跳转由 App 原生处理；抽屉的收起由插件补齐
       （官方窄屏下不会自动收起，用户需再点一次汉堡）。 */

    /* 折叠态的隐藏完全交给样式表（.hHd-Xa_collapsed 规则），
       不写任何内联样式，避免与后续展开状态互相覆盖。 */

    /* App 壳专用样式：隐藏网页顶栏样式行/设置区，悬浮汉堡与侧边栏头部 */
    function ensureShellCss() {
      if (document.getElementById("dsh-shell-css")) return;
      var st = document.createElement("style");
      st.id = "dsh-shell-css";
      st.textContent =
        ".dsh-mobile-active .hHd-Xa_logoRow{display:none !important}" +
        ".dsh-mobile-active .hHd-Xa_settingsArea{display:none !important}" +
        ".dsh-mobile-active .hHd-Xa_footArea,.dsh-mobile-active .hHd-Xa_footerActions{display:none !important}" +
        ".dsh-side-gear{width:38px;height:38px;border-radius:19px;border:none;background:transparent;color:inherit;font-size:17px;line-height:1;display:flex;align-items:center;justify-content:center;cursor:pointer;padding:0;margin-right:6px;}" +
        ".dsh-side-gear:active{background:rgba(127,127,127,0.18)}";
                                                                      document.head.appendChild(st);
    }

    function init() {
      if (MOBILE_READY) return;
      ensureShellCss();
      var ua = navigator.userAgent || "";
      var isMobile = /Mobile|Android|iPhone|iPad|iPod/i.test(ua);
      var isSmall = window.innerWidth <= 768;
      if (!isMobile && !isSmall) return;
      document.body.classList.add("dsh-mobile-active");
      var sb = findSidebar();
      if (!sb) { setTimeout(init, 500); return; }
      try {
        setupSidebar();
        setupTouch();
        bindModelMenuDrill();
        bindDrawerAutoClose();
        bindAppSettingsSection();
      } catch (e) {
        console.error("[dsh-mobile] init error:", e);
      }
      MOBILE_READY = true;
      window.__DSH_MOBILE__ = { initialized: true, toggle: function() { doToggle(findSidebar()); } };
    }

    function apply(ctx) {
      injectCss();
      if (typeof MutationObserver !== "undefined") {
        var observer = new MutationObserver(function(mutations) {
          if (!MOBILE_READY && findSidebar()) init();
        });
        observer.observe(document.body || document.documentElement, { childList: true, subtree: true });
      }
      if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
      } else {
        init();
      }
    }

    return { apply: apply };
  }
});
})();/* ===== 原生设置页深链：打开 Web 设置弹窗并切换到指定标签 =====
   由安卓原生设置页经 evaluateJavascript 调用：
   DshNativeOpenSettings(["模型","Models"])
   匹配规则：按顺序对标签文本做大小写不敏感的包含匹配，命中即点击。 */
(function () {
  function q(sel, root) { return (root || document).querySelector(sel); }
  function textOf(el) { return ((el && el.textContent) || "").trim(); }
  function clickLeaf(el) {
    var target = el;
    while (target.children.length > 0 && textOf(target.children[0]) === textOf(target)) {
      target = target.children[0];
    }
    try { target.click(); } catch (e) {}
  }

  function openDrawerIfNeeded(done) {
    var sb = q(".hHd-Xa_root");
    if (!sb || !sb.classList.contains("hHd-Xa_collapsed")) return done();
    var t = sb.querySelector(".hHd-Xa_toggle");
    if (!t) return done();
    try { t.click(); } catch (e) {}
    setTimeout(done, 280);
  }

  function clickSettingsEntry() {
    var area = q(".hHd-Xa_settingsArea") || q(".hHd-Xa_root");
    if (!area) return false;
    var leaves = area.querySelectorAll("*");
    for (var i = 0; i < leaves.length; i++) {
      var el = leaves[i];
      if (el.children.length > 0) continue;
      var tx = textOf(el);
      if (tx === "设置" || /^settings$/i.test(tx)) {
        try { el.click(); return true; } catch (e) { return false; }
      }
    }
    return false;
  }

  function waitForDialog(cb, tries) {
    var dlg = q(".VOzbGW_content");
    if (dlg) return cb(dlg);
    if (tries <= 0) return cb(null);
    setTimeout(function () { waitForDialog(cb, tries - 1); }, 150);
  }

  function switchTab(dlg, aliases) {
    var norm = [];
    for (var a = 0; a < aliases.length; a++) norm.push(String(aliases[a]).toLowerCase());
    var all = dlg.querySelectorAll("*");
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      if (el.children.length > 0) continue;
      var tx = textOf(el).toLowerCase();
      if (!tx) continue;
      for (var j = 0; j < norm.length; j++) {
        if (tx.indexOf(norm[j]) >= 0) {
          clickLeaf(el);
          return true;
        }
      }
    }
    return false;
  }

  window.DshNativeOpenSettings = function (aliases) {
    try {
      if (!Object.prototype.toString.call(aliases).includes("Array")) aliases = ["通用", "General"];
      openDrawerIfNeeded(function () {
        clickSettingsEntry();
        waitForDialog(function (dlg) {
          if (dlg) switchTab(dlg, aliases);
        }, 26);
      });
    } catch (e) { /* 静默失败：用户可自行从网页侧边栏进入设置 */ }
  };
})();
