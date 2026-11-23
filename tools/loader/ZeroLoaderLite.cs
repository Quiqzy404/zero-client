// ZERO Loader Lite - installs the ZERO client jar into a Minecraft profile's mods folder.
// Unlike ZeroLoader.cs there is NO embedded jar: it downloads the latest build from the
// zero-config-server repo (version.json -> url), so the exe stays tiny (~70 KB).
// Offline fallback: a zero-client-1.0.0.jar sitting next to the exe.
//
// LOOK: mimics the real Windows Terminal - acrylic blur, tab strip, window buttons,
// Cascadia/Consolas font, a PowerShell-style prompt. Function is unchanged.
//
// Build: csc /target:winexe /out:"ZERO Loader Lite.exe" /r:System.Management.dll /r:System.Windows.Forms.dll /r:System.Drawing.dll ZeroLoaderLite.cs
// CLI (for testing):  --check              write a report to %TEMP%\zero_loader_check.txt
//                     --install <gamedir>  install into <gamedir>\mods
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.IO;
using System.Linq;
using System.Management;
using System.Runtime.InteropServices;
using System.Text;
using System.Windows.Forms;

static class Program {
    [STAThread]
    static void Main(string[] args) {
        if (args != null && args.Length > 0) {
            Environment.ExitCode = RunCli(args);
            return;
        }
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.Run(new LoaderForm());
    }

    const string VersionUrl = "https://raw.githubusercontent.com/Quiqzy404/zero-config-server/main/dist/version.json";
    public const string JarName = "zero-client-1.0.0.jar";

    static Program() {
        try { System.Net.ServicePointManager.SecurityProtocol = System.Net.SecurityProtocolType.Tls12; } catch { }
    }

    // ---------------- download (shared by GUI and CLI)

    static string Fetch(string url, int timeout) {
        var req = (System.Net.HttpWebRequest)System.Net.HttpWebRequest.Create(url);
        req.UserAgent = "ZERO-Loader-Lite";
        req.Timeout = timeout;
        req.ReadWriteTimeout = timeout;
        using (var resp = (System.Net.HttpWebResponse)req.GetResponse())
        using (var sr = new StreamReader(resp.GetResponseStream()))
            return sr.ReadToEnd();
    }

    /** Parses a flat "key": value pair out of version.json without a JSON library. */
    static string Field(string json, string key) {
        int i = json.IndexOf("\"" + key + "\"", StringComparison.Ordinal);
        if (i < 0) return "";
        int c = json.IndexOf(':', i);
        if (c < 0) return "";
        int e = c + 1;
        while (e < json.Length && char.IsWhiteSpace(json[e])) e++;
        if (e < json.Length && json[e] == '"') {
            int q2 = json.IndexOf('"', e + 1);
            return q2 > e ? json.Substring(e + 1, q2 - e - 1) : "";
        }
        int s = e;
        while (e < json.Length && (json[e] == '-' || char.IsDigit(json[e]))) e++;
        return e > s ? json.Substring(s, e - s) : "";
    }

    /**
     * Gets a jar: download from the repo into a temp file.
     * ok=false -> err explains, file may still be a local fallback next to the exe.
     */
    public static bool GetJar(out string file, out string info, out string err) {
        file = null; info = ""; err = "";
        string url = "";
        try {
            string json = Fetch(VersionUrl, 15000);
            url = Field(json, "url");
            string build = Field(json, "build");
            if (url.Length == 0) { err = "version.json has no url"; }
            else {
                string tmp = Path.Combine(Path.GetTempPath(), "zero_loader_" + JarName);
                var req = (System.Net.HttpWebRequest)System.Net.HttpWebRequest.Create(url);
                req.UserAgent = "ZERO-Loader-Lite";
                req.Timeout = 60000;
                req.ReadWriteTimeout = 60000;
                using (var resp = (System.Net.HttpWebResponse)req.GetResponse())
                using (var src = resp.GetResponseStream())
                using (var dst = File.Create(tmp)) {
                    var buf = new byte[65536];
                    int n;
                    long total = 0;
                    while ((n = src.Read(buf, 0, buf.Length)) > 0) { dst.Write(buf, 0, n); total += n; }
                    info = "build " + build + " - " + (total / 1024).ToString() + " KB";
                }
                file = tmp;
                return true;
            }
        } catch (Exception ex) {
            err = ex.Message;
        }
        // offline fallback: jar next to the exe
        try {
            string local = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, JarName);
            if (File.Exists(local)) {
                file = local;
                info = "offline copy next to the loader";
                err = "";
                return true;
            }
        } catch { }
        if (err.Length == 0) err = "could not reach the download server";
        return false;
    }

    // ---------------- CLI

    static int RunCli(string[] args) {
        var sb = new StringBuilder();
        int code = 0;
        try {
            string report = Path.Combine(Path.GetTempPath(), "zero_loader_check.txt");
            if (args[0] == "--check") {
                sb.AppendLine("ZERO Loader Lite check " + DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss"));
                sb.AppendLine("javaw running: " + (AnyGame() ? "yes" : "no"));
                sb.AppendLine("profiles:");
                var ps = LoaderForm.FindProfiles();
                if (ps.Count == 0) sb.AppendLine("  (none)");
                foreach (var p in ps)
                    sb.AppendLine("  [" + (p.HasZero ? "x" : " ") + "] " + p.Launcher + " | " + p.Name + " | " + p.Mods +
                        " | fabric-api=" + (p.HasApi ? "yes" : "no"));
                string file, info, err;
                if (GetJar(out file, out info, out err)) sb.AppendLine("jar: OK " + info + " -> " + file);
                else { sb.AppendLine("jar: FAILED " + err); code = 1; }
                File.WriteAllText(report, sb.ToString());
            } else if (args[0] == "--install" && args.Length > 1) {
                string dir = args[1];
                sb.AppendLine("install into " + dir);
                string mods = Path.Combine(dir, "mods");
                var prof = LoaderForm.Make("(cli)", "CLI", dir, mods);
                if (LoaderForm.Running(prof)) {
                    sb.AppendLine("REFUSED: Minecraft is running with this folder");
                    code = 1;
                } else {
                    string file, info, err;
                    if (!GetJar(out file, out info, out err)) {
                        sb.AppendLine("FAILED: " + err);
                        code = 1;
                    } else {
                        Directory.CreateDirectory(mods);
                        foreach (var f in Directory.GetFiles(mods, "zero-client*.jar")) File.Delete(f);
                        string dst = Path.Combine(mods, JarName);
                        File.Copy(file, dst, true);
                        sb.AppendLine("OK " + info + " -> " + dst + " (" + new FileInfo(dst).Length + " bytes)");
                    }
                }
                File.WriteAllText(report, sb.ToString());
            } else {
                sb.AppendLine("usage: --check | --install <gamedir>");
                File.WriteAllText(report, sb.ToString());
                code = 2;
            }
        } catch (Exception ex) {
            try { File.WriteAllText(Path.Combine(Path.GetTempPath(), "zero_loader_check.txt"), "ERROR " + ex.Message); } catch { }
            code = 1;
        }
        return code;
    }

    static bool AnyGame() {
        try {
            using (var q = new ManagementObjectSearcher("SELECT ProcessId FROM Win32_Process WHERE Name='javaw.exe' OR Name='java.exe'"))
                foreach (ManagementObject o in q.Get()) return true;
        } catch { }
        return false;
    }
}

class Prof {
    public string Name, Launcher, Mods, GameDir;
    public bool HasZero, HasApi;
}

class Hit { public RectangleF R; public Action Do; public int Tag; }

class LoaderForm : Form {
    // ---- Windows Terminal "Campbell" palette
    static readonly Color Fg     = Color.FromArgb(204, 204, 204); // #CCCCCC
    static readonly Color Bright = Color.FromArgb(242, 242, 242); // #F2F2F2
    static readonly Color Dim    = Color.FromArgb(118, 118, 118); // #767676
    static readonly Color Green  = Color.FromArgb(22, 198, 12);   // #16C60C
    static readonly Color Blue   = Color.FromArgb(58, 150, 221);  // #3A96DD
    static readonly Color Cyan   = Color.FromArgb(97, 214, 214);  // #61D6D6
    static readonly Color Yellow = Color.FromArgb(249, 241, 165); // #F9F1A5
    static readonly Color Red    = Color.FromArgb(231, 72, 86);   // #E74856

    const int W = 724, H = 496, TabH = 34;
    const int BtnW = 46;                 // window buttons (min/max/close)
    const int TX = 16;                   // text left margin
    const int RowH = 26, Rows = 6;

    readonly Timer timer = new Timer();
    readonly List<Hit> hits = new List<Hit>();
    List<Prof> profiles = new List<Prof>();
    int selected = 0, hover = -1, scroll = 0;
    PointF mouse = new PointF(-1, -1);
    float t = 0;
    string status = "";
    Color statusCol = Dim;
    Point drag;
    bool dragging = false;
    bool busy = false;
    static readonly string stateDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "ZERO");
    static readonly string userName = Environment.UserName;

    public LoaderForm() {
        Text = "ZERO Loader";
        FormBorderStyle = FormBorderStyle.None;
        StartPosition = FormStartPosition.CenterScreen;
        ClientSize = new Size(W, H);
        BackColor = Color.Black;
        DoubleBuffered = true;
        SetStyle(ControlStyles.OptimizedDoubleBuffer | ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint, true);
        try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }
        Region = RoundRegion(new Rectangle(0, 0, W, H), 8);

        timer.Interval = 40;
        timer.Tick += delegate { t += 0.04f; Invalidate(); };
        timer.Start();

        MouseDown += OnDown;
        MouseMove += OnMove;
        MouseUp += OnUp;
        MouseWheel += delegate(object s, MouseEventArgs e) { scroll = Clamp(scroll - Math.Sign(e.Delta), 0, Math.Max(0, profiles.Count - Rows)); };
        KeyDown += OnKey;

        Reload();
    }

    // ---- real blur behind the window (Windows acrylic). Falls back silently on old Windows.
    [StructLayout(LayoutKind.Sequential)]
    struct AccentPolicy { public int AccentState; public int AccentFlags; public int GradientColor; public int AnimationId; }
    [StructLayout(LayoutKind.Sequential)]
    struct WinCompAttrData { public int Attribute; public IntPtr Data; public int SizeOfData; }
    [DllImport("user32.dll")]
    static extern int SetWindowCompositionAttribute(IntPtr hwnd, ref WinCompAttrData data);

    void EnableBlur() {
        try {
            // ACCENT_ENABLE_ACRYLICBLURBEHIND = 4; tint ABGR: dark neutral #0C0C0C at ~72% (Windows Terminal look)
            var accent = new AccentPolicy { AccentState = 4, GradientColor = unchecked((int)0xB80C0C0C) };
            int size = Marshal.SizeOf(accent);
            IntPtr ptr = Marshal.AllocHGlobal(size);
            Marshal.StructureToPtr(accent, ptr, false);
            var data = new WinCompAttrData { Attribute = 19 /* WCA_ACCENT_POLICY */, Data = ptr, SizeOfData = size };
            SetWindowCompositionAttribute(Handle, ref data);
            Marshal.FreeHGlobal(ptr);
        } catch { }
    }

    protected override void OnHandleCreated(EventArgs e) { base.OnHandleCreated(e); EnableBlur(); }
    protected override void OnPaintBackground(PaintEventArgs e) { /* keep the blur: do not erase */ }

    static Region RoundRegion(Rectangle r, int rad) {
        var p = new GraphicsPath();
        p.AddArc(r.X, r.Y, rad, rad, 180, 90);
        p.AddArc(r.Right - rad, r.Y, rad, rad, 270, 90);
        p.AddArc(r.Right - rad, r.Bottom - rad, rad, rad, 0, 90);
        p.AddArc(r.X, r.Bottom - rad, rad, rad, 90, 90);
        p.CloseFigure();
        return new Region(p);
    }

    static GraphicsPath Round(RectangleF r, float rad) {
        var p = new GraphicsPath();
        p.AddArc(r.X, r.Y, rad, rad, 180, 90);
        p.AddArc(r.Right - rad, r.Y, rad, rad, 270, 90);
        p.AddArc(r.Right - rad, r.Bottom - rad, rad, rad, 0, 90);
        p.AddArc(r.X, r.Bottom - rad, rad, rad, 90, 90);
        p.CloseFigure();
        return p;
    }
    static GraphicsPath RoundTop(RectangleF r, float rad) {
        var p = new GraphicsPath();
        p.AddArc(r.X, r.Y, rad, rad, 180, 90);
        p.AddArc(r.Right - rad, r.Y, rad, rad, 270, 90);
        p.AddLine(r.Right, r.Bottom, r.X, r.Bottom);
        p.CloseFigure();
        return p;
    }

    // ---------------- profile discovery (also used by the CLI)

    static IEnumerable<string> Dirs(string p) { try { return Directory.Exists(p) ? Directory.GetDirectories(p) : new string[0]; } catch { return new string[0]; } }

    public static Prof Make(string name, string launcher, string gameDir, string mods) {
        var p = new Prof { Name = name, Launcher = launcher, GameDir = gameDir, Mods = mods };
        Inspect(p);
        return p;
    }

    static void Inspect(Prof p) {
        p.HasZero = false;
        p.HasApi = p.Launcher == "NoRisk";
        if (!Directory.Exists(p.Mods)) return;
        foreach (var f in Directory.GetFiles(p.Mods, "*.jar")) {
            var n = Path.GetFileName(f).ToLowerInvariant();
            if (n.StartsWith("zero-client") || n.StartsWith("zero-loader")) p.HasZero = true;
            if (n.StartsWith("fabric-api")) p.HasApi = true;
        }
    }

    public static List<Prof> FindProfiles() {
        var r = new List<Prof>();
        string ad = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
        string mc = Path.Combine(ad, ".minecraft");
        if (Directory.Exists(mc)) r.Add(Make("Default (.minecraft)", "Minecraft", mc, Path.Combine(mc, "mods")));
        foreach (var root in new[] { @"D:\minecraft norisk\profiles", Path.Combine(ad, "norisk", "profiles"), Path.Combine(ad, "NoRiskClient", "profiles") })
            foreach (var d in Dirs(root)) r.Add(Make(Path.GetFileName(d), "NoRisk", d, Path.Combine(d, "mods")));
        foreach (var root in new[] { Path.Combine(ad, "ModrinthApp", "profiles"), Path.Combine(ad, "com.modrinth.theseus", "profiles") })
            foreach (var d in Dirs(root)) r.Add(Make(Path.GetFileName(d), "Modrinth", d, Path.Combine(d, "mods")));
        foreach (var d in Dirs(Path.Combine(ad, "PrismLauncher", "instances"))) {
            string gd = Directory.Exists(Path.Combine(d, ".minecraft")) ? Path.Combine(d, ".minecraft") : Path.Combine(d, "minecraft");
            if (Directory.Exists(gd)) r.Add(Make(Path.GetFileName(d), "Prism", gd, Path.Combine(gd, "mods")));
        }
        foreach (var d in Dirs(Path.Combine(ad, ".dawn", "profiles"))) {
            string gd = Path.Combine(d, ".minecraft");
            if (Directory.Exists(gd)) r.Add(Make(Path.GetFileName(d), "Feather", gd, Path.Combine(gd, "mods")));
        }
        try {
            string cf = Path.Combine(stateDir, "custom.txt");
            if (File.Exists(cf))
                foreach (var l in File.ReadAllLines(cf).Where(x => x.Trim().Length > 0 && Directory.Exists(x.Trim())))
                    r.Add(Make(Path.GetFileName(l.Trim()), "Custom", l.Trim(), Path.Combine(l.Trim(), "mods")));
        } catch { }
        return r.OrderByDescending(p => p.HasZero).ThenByDescending(p => p.HasApi).ToList();
    }

    void Reload() {
        string keep = (selected >= 0 && selected < profiles.Count) ? profiles[selected].GameDir : "";
        profiles = FindProfiles();
        selected = Math.Max(0, profiles.FindIndex(delegate(Prof p) { return p.GameDir == keep; }));
        if (profiles.Count == 0) selected = -1;
        UpdateStatus();
    }

    void UpdateStatus() {
        if (selected < 0 || selected >= profiles.Count) { status = "no minecraft profile found - run 'browse' to pick a folder"; statusCol = Dim; return; }
        var p = profiles[selected];
        if (Running(p)) { status = "minecraft is running with this profile - close it, then run 'load'"; statusCol = Yellow; }
        else if (!p.HasApi) { status = "no fabric api here - fine if your launcher adds it, else install fabric api 1.21.11"; statusCol = Red; }
        else if (p.HasZero) { status = "zero is installed here - run 'load' to fetch the latest build"; statusCol = Green; }
        else { status = "ready - run 'load' to download the latest build, then start this profile (right shift)"; statusCol = Dim; }
    }

    // ---------------- actions

    public static bool Running(Prof p) { return GetPid(p) != 0; }

    static int GetPid(Prof p) {
        try {
            using (var q = new ManagementObjectSearcher("SELECT ProcessId,CommandLine FROM Win32_Process WHERE Name='javaw.exe' OR Name='java.exe'"))
                foreach (ManagementObject o in q.Get())
                    if ((o["CommandLine"] ?? "").ToString().IndexOf(p.GameDir, StringComparison.OrdinalIgnoreCase) >= 0)
                        return Convert.ToInt32(o["ProcessId"]);
        } catch { }
        return 0;
    }

    static void KillMc(Prof p) {
        try {
            int pid = GetPid(p);
            if (pid == 0) return;
            var proc = System.Diagnostics.Process.GetProcessById(pid);
            proc.Kill();
            proc.WaitForExit(3000);
        } catch { }
    }

    void DoLoad() { DoLoadInner(false); }
    void DoInject() { DoLoadInner(true); }

    void DoLoadInner(bool forceKill) {
        if (selected < 0 || busy) return;
        var p = profiles[selected];
        bool running = Running(p);
        if (running && !forceKill) {
            status = "MC is running - press  inject  to force-load and restart it";
            statusCol = Yellow;
            Invalidate();
            return;
        }
        busy = true;
        status = "fetching version.json ...";
        statusCol = Dim;
        Invalidate();
        System.Threading.ThreadPool.QueueUserWorkItem(delegate {
            string file, info, err;
            bool ok = Program.GetJar(out file, out info, out err);
            try {
                if (IsDisposed) return;
                BeginInvoke((Action)delegate { FinishLoad(ok, file, info, err, p, forceKill); });
            } catch { }
        });
    }

    void FinishLoad(bool ok, string file, string info, string err, Prof p, bool forceKill) {
        busy = false;
        if (IsDisposed) return;
        if (!ok) {
            status = "download failed: " + err;
            statusCol = Red;
            Invalidate();
            return;
        }
        try {
            // kill MC before touching the mods folder (file locked otherwise)
            if (forceKill) KillMc(p);
            Directory.CreateDirectory(p.Mods);
            foreach (var f in Directory.GetFiles(p.Mods, "zero-client*.jar")) File.Delete(f);
            string dst = Path.Combine(p.Mods, Program.JarName);
            File.Copy(file, dst, true);
            Inspect(p);
            status = forceKill
                ? "injected " + info + " — MC was killed. Restart it and press Right Shift."
                : "loaded " + info + " into " + p.Name + " - start it and press right shift";
            statusCol = Green;
        } catch (IOException) {
            status = "mods folder is locked - try inject (kills MC first)";
            statusCol = Red;
        } catch (Exception ex) {
            status = "could not load: " + ex.Message;
            statusCol = Red;
        }
        Invalidate();
    }

    void DoUnload() {
        if (selected < 0) return;
        var p = profiles[selected];
        try {
            int n = 0;
            if (Directory.Exists(p.Mods)) foreach (var f in Directory.GetFiles(p.Mods, "zero-client*.jar")) { File.Delete(f); n++; }
            Inspect(p);
            status = n > 0 ? "zero removed from " + p.Name : "zero was not in this profile";
            statusCol = Dim;
        } catch (IOException) { status = "mods folder is locked - close minecraft first"; statusCol = Red; }
        Invalidate();
    }

    void Browse() {
        using (var d = new FolderBrowserDialog()) {
            d.Description = "Pick the Minecraft game folder (the one holding, or getting, a 'mods' folder)";
            if (d.ShowDialog(this) != DialogResult.OK) return;
            string dir = d.SelectedPath;
            if (Path.GetFileName(dir).Equals("mods", StringComparison.OrdinalIgnoreCase)) dir = Path.GetDirectoryName(dir);
            Directory.CreateDirectory(stateDir);
            File.AppendAllText(Path.Combine(stateDir, "custom.txt"), dir + Environment.NewLine);
            Reload();
            selected = profiles.FindIndex(delegate(Prof x) { return x.GameDir == dir; });
            if (selected < 0) selected = 0;
            UpdateStatus();
            Invalidate();
        }
    }

    // ---------------- input

    void OnKey(object s, KeyEventArgs e) {
        if (e.KeyCode == Keys.Escape) { Close(); return; }
        if (e.KeyCode == Keys.B) { Browse(); return; }
        if (e.KeyCode == Keys.R) { Reload(); Invalidate(); return; }
        if (profiles.Count == 0) return;
        if (e.KeyCode == Keys.Down) { selected = Clamp(selected + 1, 0, profiles.Count - 1); EnsureVisible(); UpdateStatus(); Invalidate(); }
        else if (e.KeyCode == Keys.Up) { selected = Clamp(selected - 1, 0, profiles.Count - 1); EnsureVisible(); UpdateStatus(); Invalidate(); }
        else if (e.KeyCode == Keys.Enter) { if (selected >= 0 && selected < profiles.Count && Running(profiles[selected])) DoInject(); else DoLoad(); }
        else if (e.KeyCode == Keys.Delete) { DoUnload(); }
        else if (e.KeyCode == Keys.O) { if (selected >= 0) { Directory.CreateDirectory(profiles[selected].Mods); System.Diagnostics.Process.Start("explorer.exe", "\"" + profiles[selected].Mods + "\""); } }
    }
    void EnsureVisible() {
        if (selected < scroll) scroll = selected;
        if (selected >= scroll + Rows) scroll = selected - Rows + 1;
        scroll = Clamp(scroll, 0, Math.Max(0, profiles.Count - Rows));
    }

    void OnDown(object s, MouseEventArgs e) {
        foreach (var h in hits) if (h.R.Contains(e.Location)) { if (h.Tag >= 0) { selected = h.Tag; UpdateStatus(); Invalidate(); } else if (h.Do != null) h.Do(); return; }
        if (e.Y < TabH) { dragging = true; drag = e.Location; }
    }
    void OnMove(object s, MouseEventArgs e) {
        if (dragging) { Location = new Point(Location.X + e.X - drag.X, Location.Y + e.Y - drag.Y); return; }
        mouse = e.Location;
        int h = -1;
        for (int i = 0; i < hits.Count; i++) if (hits[i].R.Contains(e.Location)) h = i;
        hover = h;
        Cursor = h >= 0 ? Cursors.Hand : Cursors.Default;
    }
    void OnUp(object s, MouseEventArgs e) { dragging = false; }

    // ---------------- paint

    protected override void OnPaint(PaintEventArgs e) {
        var g = e.Graphics;
        g.SmoothingMode = SmoothingMode.AntiAlias;
        g.TextRenderingHint = TextRenderingHint.AntiAlias; // crisp over the blur (no ClearType fringe)
        hits.Clear();

        // ---- tab strip (slightly darker than the acrylic body)
        using (var b = new SolidBrush(Color.FromArgb(120, 24, 24, 24))) g.FillRectangle(b, 0, 0, W, TabH);

        // active tab chip
        var tab = new RectangleF(6, 6, 208, TabH - 6);
        using (var p = RoundTop(tab, 7f)) using (var b = new SolidBrush(Color.FromArgb(150, 12, 12, 12))) g.FillPath(b, p);
        Mono(g, ">_", 9.5f, Fg, tab.X + 12, tab.Y + 5); // tab icon (terminal)
        Mono(g, "zero loader", 9.5f, Bright, tab.X + 36, tab.Y + 5);
        var tabX = new RectangleF(tab.Right - 24, tab.Y + 5, 18, 18);
        bool tabXHov = tabX.Contains(mouse);
        if (tabXHov) using (var b = new SolidBrush(Color.FromArgb(40, 255, 255, 255))) g.FillEllipse(b, tabX);
        using (var pen = new Pen(tabXHov ? Bright : Dim, 1.2f)) { g.DrawLine(pen, tabX.X + 5, tabX.Y + 5, tabX.Right - 5, tabX.Bottom - 5); g.DrawLine(pen, tabX.Right - 5, tabX.Y + 5, tabX.X + 5, tabX.Bottom - 5); }
        hits.Add(new Hit { R = tabX, Tag = -1, Do = Close });

        // "+" new tab + dropdown chevron (reused as: + = browse, v = refresh)
        var plus = new RectangleF(tab.Right + 6, 7, 26, 22);
        TabGlyph(g, plus, "+", Browse);
        var chev = new RectangleF(plus.Right + 2, 7, 24, 22);
        TabGlyph(g, chev, "v", delegate { Reload(); Invalidate(); });

        // ---- window buttons (min / max / close), Windows order left -> right
        WinBtn(g, W - BtnW * 3, 2, delegate { WindowState = FormWindowState.Minimized; }); // minimize
        WinBtn(g, W - BtnW * 2, 1, delegate { });                                           // maximize (no-op)
        WinBtn(g, W - BtnW, 0, Close);                                                      // close (red on hover)

        // ---- body: minimal console loader (like a real cheat loader inside Windows Terminal)
        float y = TabH + 16;
        Mono(g, "zero", 11.5f, Bright, TX, y);
        float hx = TX + Tw(g, "zero", 11.5f);
        Mono(g, ".client", 11.5f, Dim, hx, y); hx += Tw(g, ".client", 11.5f);
        Mono(g, "  loader", 11.5f, Dim, hx, y);
        if (((int)(t * 1.6f)) % 2 == 0) using (var b = new SolidBrush(Color.FromArgb(220, 204, 204, 204))) g.FillRectangle(b, hx + Tw(g, "  loader ", 11.5f), y + 2, 9, 16);
        y += 26;
        Mono(g, "payload  ", 9.5f, Dim, TX, y);
        float payx = TX + Tw(g, "payload  ", 9.5f);
        string pl = "github:Quiqzy404/zero-config-server";
        Mono(g, pl, 9.5f, Fg, payx, y); payx += Tw(g, pl, 9.5f);
        Mono(g, "  (latest)  [fabric 1.21.11]", 9.5f, Dim, payx, y);
        y += 30;
        int listTop = (int)y;

        // ---- target list (profiles) - minimal single-line rows with a ► marker
        if (profiles.Count == 0) Mono(g, "no minecraft profiles found - press  b  to browse for a folder", 10f, Dim, TX, listTop + 4);
        for (int i = 0; i < profiles.Count; i++) {
            int row = i - scroll;
            if (row < 0 || row >= Rows) continue;
            var r = new RectangleF(TX - 6, listTop + row * RowH, W - TX * 2 + 12, RowH - 2);
            var pr = profiles[i];
            bool sel = i == selected;
            bool hov = r.Contains(mouse);
            if (hov && !sel) using (var p = Round(r, 4f)) using (var b = new SolidBrush(Color.FromArgb(20, 255, 255, 255))) g.FillPath(b, p);
            float cx = r.X + 8;
            Mono(g, sel ? "►" : " ", 10f, sel ? Fg : Dim, cx, r.Y + 4); cx += Tw(g, "x  ", 10f);
            Mono(g, pr.Name, 10f, sel ? Bright : Fg, cx, r.Y + 4); cx += Tw(g, pr.Name + "    ", 10f);
            string det = pr.Launcher.ToLowerInvariant();
            if (pr.Launcher != "NoRisk" && !pr.HasApi) det += "  no-api";
            if (pr.HasZero) det += "  · installed";
            Mono(g, det, 9f, Dim, cx, r.Y + 5);
            hits.Add(new Hit { R = r, Tag = i });
        }
        if (profiles.Count > Rows) {
            float trackH = RowH * Rows;
            float th = trackH * (float)Rows / profiles.Count;
            float ty = listTop + trackH * (float)scroll / profiles.Count;
            using (var b = new SolidBrush(Color.FromArgb(110, 120, 120, 120))) g.FillRectangle(b, W - TX + 2, ty, 3, th);
        }

        // ---- status line
        float sy = listTop + RowH * Rows + 12;
        MonoWrap(g, status, 9.5f, statusCol, new RectangleF(TX, sy, W - TX * 2, 40));

        // ---- footer keybinds (like: up/down select   enter inject   esc quit)
        float fy = H - 30;
        float fx = TX;
        bool mcRunning = selected >= 0 && selected < profiles.Count && Running(profiles[selected]);
        fx = Key(g, "↑/↓", "select", fx, fy, null);
        if (mcRunning)
            fx = Key(g, "enter", busy ? "working" : "inject", fx, fy, busy ? (Action)null : DoInject);
        else
            fx = Key(g, "enter", busy ? "working" : "load", fx, fy, busy ? (Action)null : DoLoad);
        fx = Key(g, "del", "unload", fx, fy, DoUnload);
        fx = Key(g, "o", "folder", fx, fy, delegate { if (selected >= 0) { Directory.CreateDirectory(profiles[selected].Mods); System.Diagnostics.Process.Start("explorer.exe", "\"" + profiles[selected].Mods + "\""); } });
        fx = Key(g, "b", "browse", fx, fy, Browse);
        fx = Key(g, "r", "reload", fx, fy, delegate { Reload(); Invalidate(); });
        fx = Key(g, "esc", "quit", fx, fy, Close);

        // subtle outer edge
        using (var pen = new Pen(Color.FromArgb(60, 255, 255, 255))) using (var p = Round(new RectangleF(0.5f, 0.5f, W - 1, H - 1), 8)) g.DrawPath(pen, p);
    }

    /** PowerShell prompt head: "PS C:\Users\name> " ; returns x after it. */
    float PromptHead(Graphics g, float x, float y) {
        Mono(g, "PS ", 10f, Fg, x, y); x += Tw(g, "PS ", 10f);
        string path = "C:\\Users\\" + userName;
        Mono(g, path, 10f, Blue, x, y); x += Tw(g, path, 10f);
        Mono(g, "> ", 10f, Fg, x, y); x += Tw(g, "> ", 10f);
        return x;
    }
    void Prompt(Graphics g, float x, float y, string cmd) {
        float cx = PromptHead(g, x, y);
        Mono(g, cmd, 10f, Bright, cx, y);
    }

    /** Footer keybind hint: bright key + dim label; clickable if act != null. Returns next x. */
    float Key(Graphics g, string key, string label, float x, float y, Action act) {
        float kw = Tw(g, key, 9f);
        Mono(g, key, 9f, Fg, x, y);
        float lx = x + kw + 4;
        Mono(g, label, 9f, Dim, lx, y);
        float end = lx + Tw(g, label, 9f);
        if (act != null) hits.Add(new Hit { R = new RectangleF(x - 3, y - 2, end - x + 8, 18), Tag = -1, Do = act });
        return end + 18;
    }

    void TabGlyph(Graphics g, RectangleF r, string glyph, Action act) {
        bool hov = r.Contains(mouse);
        if (hov) using (var p = Round(r, 5f)) using (var b = new SolidBrush(Color.FromArgb(34, 255, 255, 255))) g.FillPath(b, p);
        using (var f = MonoFont(11f, FontStyle.Regular)) using (var b = new SolidBrush(hov ? Bright : Dim))
        using (var sf = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center })
            g.DrawString(glyph, f, b, r, sf);
        hits.Add(new Hit { R = r, Tag = -1, Do = act });
    }

    /** Window caption button. kind: 2=minimize (line), 1=maximize (square), 0=close (X). */
    void WinBtn(Graphics g, float x, int kind, Action act) {
        var r = new RectangleF(x, 0, BtnW, TabH);
        bool hov = r.Contains(mouse);
        if (hov) using (var b = new SolidBrush(kind == 0 ? Color.FromArgb(230, 232, 17, 35) : Color.FromArgb(36, 255, 255, 255))) g.FillRectangle(b, r);
        float cxp = x + BtnW / 2f, cyp = TabH / 2f;
        using (var pen = new Pen(hov && kind == 0 ? Color.White : Color.FromArgb(230, 230, 230, 230), 1.2f)) {
            if (kind == 2) g.DrawLine(pen, cxp - 5, cyp, cxp + 5, cyp);
            else if (kind == 1) g.DrawRectangle(pen, cxp - 5, cyp - 5, 10, 10);
            else { g.DrawLine(pen, cxp - 5, cyp - 5, cxp + 5, cyp + 5); g.DrawLine(pen, cxp + 5, cyp - 5, cxp - 5, cyp + 5); }
        }
        if (act != null) hits.Add(new Hit { R = r, Tag = -1, Do = act });
    }

    // ---- monospace helpers
    static Font MonoFont(float sz, FontStyle st) {
        try { var f = new Font("Cascadia Mono", sz, st); if (f.Name.IndexOf("Cascadia", StringComparison.OrdinalIgnoreCase) >= 0) return f; f.Dispose(); } catch { }
        try { var f = new Font("Cascadia Code", sz, st); if (f.Name.IndexOf("Cascadia", StringComparison.OrdinalIgnoreCase) >= 0) return f; f.Dispose(); } catch { }
        return new Font("Consolas", sz, st);
    }
    void Mono(Graphics g, string s, float sz, Color c, float x, float y) {
        using (var f = MonoFont(sz, FontStyle.Regular)) using (var b = new SolidBrush(c)) g.DrawString(s, f, b, x, y);
    }
    void MonoWrap(Graphics g, string s, float sz, Color c, RectangleF r) {
        using (var f = MonoFont(sz, FontStyle.Regular)) using (var b = new SolidBrush(c)) g.DrawString(s, f, b, r);
    }
    float Tw(Graphics g, string s, float sz) {
        using (var f = MonoFont(sz, FontStyle.Regular)) return g.MeasureString(s, f, 10000, StringFormat.GenericTypographic).Width;
    }

    static int Clamp(int v, int a, int b) { return v < a ? a : v > b ? b : v; }

    protected override void OnFormClosed(FormClosedEventArgs e) { timer.Stop(); base.OnFormClosed(e); }
}
