package com.adfmig.report;

/**
 * The report's stylesheet, inlined so the file stays self-contained.
 *
 * <p>A report is emailed, forwarded and printed. It cannot depend on a network, a font service or
 * a CDN — customers open these inside networks that reach none of them.
 */
final class ReportStyle {

    private ReportStyle() {}

    static final String CSS = """
            :root {
              --ink: #16181d; --muted: #5b6270; --faint: #8a919e;
              --rule: #e3e6ec; --panel: #f7f8fa; --page: #ffffff;
              --auto: #2f7d4f; --assisted: #b3701a; --manual: #b23c3c; --rewrite: #6a5296;
              --accent: #23458c;
            }
            @media (prefers-color-scheme: dark) {
              :root:not([data-theme="light"]) {
                --ink: #e7e9ee; --muted: #a2a9b8; --faint: #7d8494;
                --rule: #2b303a; --panel: #171a21; --page: #0f1116;
                --auto: #6cc48c; --assisted: #e0a95a; --manual: #e08080; --rewrite: #a894d6;
                --accent: #7ea2e8;
              }
            }
            * { box-sizing: border-box; }
            body {
              margin: 0; background: var(--page); color: var(--ink);
              font: 15px/1.6 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
              -webkit-font-smoothing: antialiased;
            }
            .page { max-width: 980px; margin: 0 auto; padding: 56px 32px 96px; }
            header { border-bottom: 2px solid var(--ink); padding-bottom: 20px; margin-bottom: 40px; }
            .eyebrow { font-size: 12px; letter-spacing: .12em; text-transform: uppercase; color: var(--muted); }
            h1 { font-size: 30px; line-height: 1.2; margin: 10px 0 6px; font-weight: 650; letter-spacing: -.01em; }
            h2 {
              font-size: 13px; letter-spacing: .1em; text-transform: uppercase; color: var(--muted);
              margin: 48px 0 16px; padding-bottom: 8px; border-bottom: 1px solid var(--rule); font-weight: 600;
            }
            h3 { font-size: 15px; margin: 28px 0 10px; font-weight: 620; }
            p { margin: 0 0 12px; max-width: 74ch; }
            .sub { color: var(--muted); font-size: 14px; }
            .note { color: var(--muted); font-size: 13.5px; max-width: 74ch; }

            .headline { display: flex; flex-wrap: wrap; gap: 12px; margin: 28px 0 8px; }
            .stat {
              flex: 1 1 150px; background: var(--panel); border: 1px solid var(--rule);
              border-radius: 8px; padding: 16px 18px;
            }
            .stat .value { font-size: 27px; font-weight: 650; letter-spacing: -.02em; }
            .stat .label { font-size: 12px; color: var(--muted); margin-top: 3px; }

            table { width: 100%; border-collapse: collapse; margin: 14px 0 8px; font-size: 14px; }
            th {
              text-align: left; font-size: 11.5px; letter-spacing: .07em; text-transform: uppercase;
              color: var(--muted); font-weight: 600; padding: 8px 10px; border-bottom: 1px solid var(--rule);
            }
            td { padding: 9px 10px; border-bottom: 1px solid var(--rule); vertical-align: top; }
            td.num, th.num { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
            tbody tr:last-child td { border-bottom: none; }
            .mono { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12.5px; }
            .scroll { overflow-x: auto; }

            .tag {
              display: inline-block; font-size: 11px; font-weight: 600; letter-spacing: .04em;
              text-transform: uppercase; padding: 2px 8px; border-radius: 999px;
              border: 1px solid currentColor; white-space: nowrap;
            }
            .AUTO { color: var(--auto); } .ASSISTED { color: var(--assisted); }
            .MANUAL { color: var(--manual); } .REWRITE { color: var(--rewrite); }

            .bar { height: 8px; border-radius: 4px; background: var(--rule); overflow: hidden; display: flex; }
            .bar span { display: block; height: 100%; }
            .bar .AUTO { background: var(--auto); } .bar .ASSISTED { background: var(--assisted); }
            .bar .MANUAL { background: var(--manual); } .bar .REWRITE { background: var(--rewrite); }
            .legend { display: flex; flex-wrap: wrap; gap: 16px; margin-top: 10px; font-size: 13px; color: var(--muted); }
            .legend b { color: var(--ink); font-weight: 600; }
            .swatch { display: inline-block; width: 9px; height: 9px; border-radius: 2px; margin-right: 6px; }

            .callout {
              border-left: 3px solid var(--manual); background: var(--panel);
              padding: 14px 18px; margin: 16px 0; border-radius: 0 6px 6px 0;
            }
            .callout h3 { margin-top: 0; color: var(--manual); }
            .callout.info { border-left-color: var(--accent); }
            .callout.info h3 { color: var(--accent); }
            .drivers { color: var(--muted); font-size: 12.5px; }
            footer { margin-top: 64px; padding-top: 20px; border-top: 1px solid var(--rule); color: var(--faint); font-size: 12.5px; }

            @media print {
              body { background: #fff; color: #000; }
              .page { padding: 0; max-width: none; }
              h2 { break-after: avoid; } tr { break-inside: avoid; }
            }
            """;
}
