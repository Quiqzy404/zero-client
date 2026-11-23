// ZERO Loader (console) - a REAL console program. On Windows 11 it is hosted by the real
// Windows Terminal (tabs, acrylic, Cascadia font) - so it looks 1:1 like Windows Terminal
// because it literally runs inside it. It downloads the latest ZERO build from the
// zero-config-server repo and installs it into a Minecraft profile's mods folder.
//
// Build: csc /target:exe /out:"zero-loader.exe" /r:System.Management.dll ZeroLoaderConsole.cs
// Keys:  up/down select   enter load   del unload   o folder   b browse   r reload   esc quit
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Management;
using System.Runtime.InteropServices;
using System.Text;

class Prof { public string Name, Launcher, Mods, GameDir, Server; public bool HasZero, HasApi; }

static class ZeroLoaderConsole {
    const string VersionUrl = "https://raw.githubusercontent.com/Quiqzy404/zero-config-server/main/dist/version.json";
    const string JarName = "zero-client-1.0.0.jar";
    static readonly string stateDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "ZERO");

    // ---- ANSI colors (truecolor; Windows Terminal supports it natively)
    const string R  = "\x1b[0m";
    const string DIM= "\x1b[38;2;118;118;118m";
    const string FG = "\x1b[38;2;204;204;204m";
    const string WH = "\x1b[38;2;242;242;242m";
    const string GR = "\x1b[38;2;22;198;12m";
    const string RD = "\x1b[38;2;231;72;86m";
    const string YL = "\x1b[38;2;249;241;165m";
    const string CY = "\x1b[38;2;97;214;214m";

    static List<Prof> profiles = new List<Prof>();
    static int selected = 0;
    static string status = "";
    static string statusCol = DIM;

    [DllImport("kernel32.dll")] static extern IntPtr GetStdHandle(int n);
    [DllImport("kernel32.dll")] static extern bool GetConsoleMode(IntPtr h, out int m);
    [DllImport("kernel32.dll")] static extern bool SetConsoleMode(IntPtr h, int m);

    [STAThread]
    static int Main(string[] args) {
        try { System.Net.ServicePointManager.SecurityProtocol = System.Net.SecurityProtocolType.Tls12; } catch { }
        if (args != null && args.Length > 0) return RunCli(args);

        EnableAnsi();
        try { Console.Title = "zero loader"; } catch { }
        try { Console.OutputEncoding = Encoding.UTF8; } catch { }
        Console.CursorVisible = false;
        Console.Write("\x1b[?1049h\x1b[?25l"); // enter alternate screen, hide cursor
        Reload();

        bool run = true;
        while (run) {
            Render();
            var k = Console.ReadKey(true);
            switch (k.Key) {
                case ConsoleKey.Escape: run = false; break;
                case ConsoleKey.UpArrow: if (profiles.Count > 0) selected = (selected - 1 + profiles.Count) % profiles.Count; UpdateStatus(); break;
                case ConsoleKey.DownArrow: if (profiles.Count > 0) selected = (selected + 1) % profiles.Count; UpdateStatus(); break;
                case ConsoleKey.Enter: DoLoad(); break;
                case ConsoleKey.Delete: DoUnload(); break;
                case ConsoleKey.O: OpenFolder(); break;
                case ConsoleKey.B: BrowseInput(); break;
                case ConsoleKey.R: Reload(); break;
            }
        }
        Console.Write("\x1b[?1049l\x1b[?25h"); // leave alternate screen, show cursor
        Console.Write(R);
        return 0;
    }

    static void EnableAnsi() {
        try {
            IntPtr h = GetStdHandle(-11); // STD_OUTPUT_HANDLE
            int mode; if (GetConsoleMode(h, out mode)) SetConsoleMode(h, mode | 0x0004); // ENABLE_VIRTUAL_TERMINAL_PROCESSING
        } catch { }
    }

    // ---------------- UI

    static void Render() {
        Console.CursorVisible = false;
        var sb = new StringBuilder();
        sb.Append("\x1b[2J\x1b[H"); // clear screen + cursor home
        sb.Append('\n');
        sb.Append("  " + WH + "zero" + DIM + ".client" + DIM + "  loader" + R + "\n");
        sb.Append("  " + DIM + "payload  " + FG + "github:Quiqzy404/zero-config-server" + DIM + "  (latest)  [fabric 1.21.11]" + R + "\n");
        sb.Append('\n');

        if (profiles.Count == 0) {
            sb.Append("  " + DIM + "no running minecraft instance - start mc, then press  " + FG + "r" + DIM + "  to reload" + R + "\n");
        } else {
            const int VIS = 8;
            int off = 0;
            if (profiles.Count > VIS) { off = selected - VIS / 2; if (off < 0) off = 0; if (off > profiles.Count - VIS) off = profiles.Count - VIS; }
            int end = Math.Min(profiles.Count, off + VIS);
            sb.Append("  " + DIM + (off > 0 ? "\u2191 " + off + " more above" : "") + R + "\n");
            for (int i = off; i < end; i++) {
                var p = profiles[i];
                bool sel = i == selected;
                string mark = sel ? FG + "\u25BA " : "  ";
                string name = (sel ? WH : FG) + p.Name;
                string det = DIM + "   " + p.Launcher.ToLowerInvariant();
                if (p.Server != null && p.Server.Length > 0) det += "  \u00B7 " + CY + p.Server + DIM;
                if (p.Launcher != "NoRisk" && !p.HasApi) det += "  no-api";
                if (p.HasZero) det += "  \u00B7 installed";
                sb.Append("  " + mark + name + det + R + "\n");
            }
            sb.Append("  " + DIM + (end < profiles.Count ? "\u2193 " + (profiles.Count - end) + " more below" : "") + R + "\n");
        }
        sb.Append('\n');
        sb.Append("  " + statusCol + status + R + "\n");
        sb.Append('\n');
        sb.Append("  " + FG + "\u2191/\u2193" + DIM + " select   " + FG + "enter" + DIM + " load   "
                + FG + "del" + DIM + " unload   " + FG + "o" + DIM + " folder   "
                + FG + "b" + DIM + " browse   " + FG + "r" + DIM + " reload   " + FG + "esc" + DIM + " quit" + R + "\n");
        Console.Write(sb.ToString());
    }

    static void Line(string s) { Console.Write(s + R + "\n"); }

    // ---------------- profiles

    static IEnumerable<string> Dirs(string p) { try { return Directory.Exists(p) ? Directory.GetDirectories(p) : new string[0]; } catch { return new string[0]; } }

    static Prof Make(string name, string launcher, string gameDir, string mods) {
        var p = new Prof { Name = name, Launcher = launcher, GameDir = gameDir, Mods = mods };
        Inspect(p); return p;
    }

    static void Inspect(Prof p) {
        p.HasZero = false;
        p.HasApi = p.Launcher == "NoRisk";
        p.Server = "";
        if (!Directory.Exists(p.Mods)) return;
        foreach (var f in Directory.GetFiles(p.Mods, "*.jar")) {
            var n = Path.GetFileName(f).ToLowerInvariant();
            if (n.StartsWith("zero-client") || n.StartsWith("zero-loader")) p.HasZero = true;
            if (n.StartsWith("fabric-api")) p.HasApi = true;
        }
        // read last "Connecting to" line from logs/latest.log
        try {
            string log = Path.Combine(p.GameDir, "logs", "latest.log");
            if (File.Exists(log)) {
                string last = "";
                // read last 200 lines to avoid huge log load
                var lines = File.ReadAllLines(log);
                for (int i = lines.Length - 1; i >= Math.Max(0, lines.Length - 200); i--) {
                    if (lines[i].IndexOf("Connecting to", StringComparison.OrdinalIgnoreCase) >= 0) { last = lines[i]; break; }
                }
                if (last.Length > 0) {
                    // format: [HH:MM:SS] [Render thread/INFO]: Connecting to <host>, <port>
                    int idx = last.IndexOf("Connecting to", StringComparison.OrdinalIgnoreCase);
                    if (idx >= 0) {
                        string rest = last.Substring(idx + 13).Trim().TrimEnd('.');
                        // strip port if present: "hypixel.net, 25565" -> "hypixel.net"
                        int comma = rest.IndexOf(',');
                        if (comma > 0) rest = rest.Substring(0, comma).Trim();
                        p.Server = rest;
                    }
                }
            }
        } catch { }
    }

    static List<Prof> FindProfiles() {
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

    static void Reload() {
        string keep = (selected >= 0 && selected < profiles.Count) ? profiles[selected].GameDir : "";
        var all = FindProfiles();
        // keep only running profiles, deduplicated by PID (one process = one entry)
        var seen = new System.Collections.Generic.HashSet<int>();
        var running = new List<Prof>();
        foreach (var p in all) {
            int pid = GetPid(p);
            if (pid > 0 && seen.Add(pid)) running.Add(p);
        }
        profiles = running;
        selected = Math.Max(0, profiles.FindIndex(delegate(Prof p) { return p.GameDir == keep; }));
        if (profiles.Count == 0) selected = 0;
        UpdateStatus();
    }

    static void UpdateStatus() {
        if (profiles.Count == 0) { status = "no running minecraft instance found - start mc, then press r"; statusCol = DIM; return; }
        var p = profiles[selected];
        if (Running(p)) { status = "mc is running - press enter to inject zero live (no restart needed)"; statusCol = CY; }
        else if (!p.HasApi) { status = "no fabric api here - install fabric api 1.21.11 (ok if your launcher adds it)"; statusCol = RD; }
        else if (p.HasZero) { status = "zero installed - press enter to fetch the latest build"; statusCol = GR; }
        else { status = "ready - press enter to download the latest build"; statusCol = DIM; }
    }

    static int GetPid(Prof p) {
        try {
            using (var q = new ManagementObjectSearcher("SELECT ProcessId,CommandLine FROM Win32_Process WHERE Name='javaw.exe' OR Name='java.exe'"))
                foreach (ManagementObject o in q.Get()) {
                    string cl = (o["CommandLine"] ?? "").ToString();
                    if (cl.IndexOf(p.GameDir, StringComparison.OrdinalIgnoreCase) >= 0)
                        return Convert.ToInt32(o["ProcessId"]);
                }
        } catch { }
        return -1;
    }

    static bool HasAttach(string javaExe) {
        // JDK 9+: attach.dll in bin/ or lib/
        try {
            string bin = Path.GetDirectoryName(javaExe);
            string lib = Path.Combine(Path.GetDirectoryName(bin), "lib");
            if (File.Exists(Path.Combine(bin, "attach.dll"))) return true;
            if (File.Exists(Path.Combine(lib, "attach.dll"))) return true;
            // JDK 11+: jdk.attach module in jmods/
            string jmods = Path.Combine(Path.GetDirectoryName(bin), "jmods", "jdk.attach.jmod");
            if (File.Exists(jmods)) return true;
            // presence of javac.exe = JDK (not JRE) → assume attach available
            if (File.Exists(Path.Combine(bin, "javac.exe"))) return true;
        } catch { }
        return false;
    }

    static string GetJavaExe(int pid) {
        // 1. JAVA_HOME — trust it if it points at a JDK
        string javaHome = System.Environment.GetEnvironmentVariable("JAVA_HOME") ?? "";
        if (javaHome.Length > 0) {
            string j = Path.Combine(javaHome, "bin", "java.exe");
            if (File.Exists(j) && HasAttach(j)) return j;
            if (File.Exists(j)) return j; // even without attach check, trust JAVA_HOME
        }
        // 2. PATH: first java.exe that has attach
        string pathEnv = System.Environment.GetEnvironmentVariable("PATH") ?? "";
        foreach (string dir in pathEnv.Split(';')) {
            try {
                string candidate = Path.Combine(dir.Trim(), "java.exe");
                if (File.Exists(candidate) && HasAttach(candidate)) return candidate;
            } catch { }
        }
        // 3. fallback: MC JRE (may not have attach — but we tried everything else)
        try {
            using (var q = new ManagementObjectSearcher("SELECT ExecutablePath FROM Win32_Process WHERE ProcessId=" + pid))
                foreach (ManagementObject o in q.Get()) {
                    string ep = (o["ExecutablePath"] ?? "").ToString();
                    if (ep.Length > 0) return ep;
                }
        } catch { }
        return "java";
    }

    static string DoAttachInject(int pid, string modJarPath) {
        string baseDir = AppDomain.CurrentDomain.BaseDirectory;
        string agentJar = Path.Combine(baseDir, "zero-agent.jar");
        if (!File.Exists(agentJar)) return "zero-agent.jar not found next to exe";
        string javaExe = GetJavaExe(pid);
        string flags = "-Djdk.attach.allowAttachSelf=true --add-modules jdk.attach";
        // Injector scans for MC via VirtualMachineDescriptor, then calls loadAgent(agentJar, modJarPath)
        // args: <agentJarPath> <modJarPath>  (NOT pid — injector finds MC itself)
        string args = flags + " -jar \"" + agentJar + "\" \"" + agentJar + "\" \"" + modJarPath + "\"";
        string toolsJar = Path.Combine(Path.GetDirectoryName(Path.GetDirectoryName(javaExe)), "lib", "tools.jar");
        if (File.Exists(toolsJar)) args = flags + " -cp \"" + toolsJar + "\" -jar \"" + agentJar + "\" \"" + agentJar + "\" \"" + modJarPath + "\"";
        try {
            var psi = new System.Diagnostics.ProcessStartInfo {
                FileName = javaExe, Arguments = args,
                UseShellExecute = false, RedirectStandardOutput = true,
                RedirectStandardError = true, CreateNoWindow = true
            };
            using (var proc = System.Diagnostics.Process.Start(psi)) {
                string stdout = proc.StandardOutput.ReadToEnd();
                string stderr = proc.StandardError.ReadToEnd();
                proc.WaitForExit(12000);
                if (proc.ExitCode != 0) return (stderr.Length > 0 ? stderr.Trim() : "exit " + proc.ExitCode);
                return null;
            }
        } catch (Exception ex) { return ex.Message; }
    }

    static void DoInject() {
        if (profiles.Count == 0) return;
        var p = profiles[selected];
        int pid = GetPid(p);
        if (pid < 0) { status = "mc not found"; statusCol = RD; return; }
        status = "fetching version.json ..."; statusCol = DIM; Render();
        string file, info, err;
        if (!GetJar(out file, out info, out err)) { status = "download failed: " + err; statusCol = RD; return; }
        // inject from temp — works even when mods folder is locked (NRC)
        string dst = Path.Combine(Path.GetTempPath(), JarName);
        try { File.Copy(file, dst, true); }
        catch (Exception ex) { status = "copy to temp failed: " + ex.Message; statusCol = RD; return; }
        // best-effort: also write to mods for persistence after restart
        try {
            Directory.CreateDirectory(p.Mods);
            foreach (var f in Directory.GetFiles(p.Mods, "zero-client*.jar")) File.Delete(f);
            File.Copy(file, Path.Combine(p.Mods, JarName), true);
            Inspect(p);
        } catch { /* locked is fine */ }
        status = "injecting " + info + " ..."; statusCol = DIM; Render();
        string injectErr = DoAttachInject(pid, dst);
        if (injectErr != null) { status = "inject failed: " + injectErr; statusCol = RD; }
        else { status = "injected " + info + " live - press right shift in-game"; statusCol = GR; }
    }

    static bool Running(Prof p) {
        try {
            using (var q = new ManagementObjectSearcher("SELECT CommandLine FROM Win32_Process WHERE Name='javaw.exe' OR Name='java.exe'"))
                foreach (ManagementObject o in q.Get())
                    if ((o["CommandLine"] ?? "").ToString().IndexOf(p.GameDir, StringComparison.OrdinalIgnoreCase) >= 0) return true;
        } catch { }
        return false;
    }

    // ---------------- actions

    static void DoLoad() {
        if (profiles.Count == 0) return;
        var p = profiles[selected];
        if (Running(p)) { DoInject(); return; }
        status = "fetching version.json ..."; statusCol = DIM; Render();
        string file, info, err;
        if (!GetJar(out file, out info, out err)) { status = "download failed: " + err; statusCol = RD; return; }
        try {
            Directory.CreateDirectory(p.Mods);
            foreach (var f in Directory.GetFiles(p.Mods, "zero-client*.jar")) File.Delete(f);
            File.Copy(file, Path.Combine(p.Mods, JarName), true);
            Inspect(p);
            status = Running(p) ? "loaded (" + info + ") - minecraft open, restart to use zero"
                                : "loaded " + info + " into " + p.Name + " - start it, press right shift";
            statusCol = Running(p) ? YL : GR;
        } catch (IOException) { status = "mods folder locked - close minecraft and press enter again"; statusCol = RD; }
        catch (Exception ex) { status = "could not load: " + ex.Message; statusCol = RD; }
    }

    static void DoUnload() {
        if (profiles.Count == 0) return;
        var p = profiles[selected];
        try {
            int n = 0;
            if (Directory.Exists(p.Mods)) foreach (var f in Directory.GetFiles(p.Mods, "zero-client*.jar")) { File.Delete(f); n++; }
            Inspect(p);
            status = n > 0 ? "zero removed from " + p.Name : "zero was not in this profile"; statusCol = DIM;
        } catch (IOException) { status = "mods folder locked - close minecraft first"; statusCol = RD; }
    }

    static void OpenFolder() {
        if (profiles.Count == 0) return;
        try { Directory.CreateDirectory(profiles[selected].Mods); System.Diagnostics.Process.Start("explorer.exe", "\"" + profiles[selected].Mods + "\""); } catch { }
    }

    static void BrowseInput() {
        Console.CursorVisible = true;
        Console.Write("\n  " + FG + "paste the minecraft game folder path, then Enter: " + R);
        string dir = (Console.ReadLine() ?? "").Trim().Trim('"');
        Console.CursorVisible = false;
        if (dir.Length == 0) { UpdateStatus(); return; }
        if (Path.GetFileName(dir).Equals("mods", StringComparison.OrdinalIgnoreCase)) dir = Path.GetDirectoryName(dir);
        if (!Directory.Exists(dir)) { status = "that folder does not exist"; statusCol = RD; return; }
        try { Directory.CreateDirectory(stateDir); File.AppendAllText(Path.Combine(stateDir, "custom.txt"), dir + Environment.NewLine); } catch { }
        Reload();
        int idx = profiles.FindIndex(delegate(Prof x) { return x.GameDir == dir; });
        if (idx >= 0) selected = idx;
        UpdateStatus();
    }

    // ---------------- download

    static string Fetch(string url, int timeout) {
        var req = (System.Net.HttpWebRequest)System.Net.HttpWebRequest.Create(url);
        req.UserAgent = "ZERO-Loader"; req.Timeout = timeout; req.ReadWriteTimeout = timeout;
        using (var resp = (System.Net.HttpWebResponse)req.GetResponse())
        using (var sr = new StreamReader(resp.GetResponseStream())) return sr.ReadToEnd();
    }

    static string Field(string json, string key) {
        int i = json.IndexOf("\"" + key + "\"", StringComparison.Ordinal);
        if (i < 0) return "";
        int c = json.IndexOf(':', i); if (c < 0) return "";
        int e = c + 1; while (e < json.Length && char.IsWhiteSpace(json[e])) e++;
        if (e < json.Length && json[e] == '"') { int q2 = json.IndexOf('"', e + 1); return q2 > e ? json.Substring(e + 1, q2 - e - 1) : ""; }
        int s = e; while (e < json.Length && (json[e] == '-' || char.IsDigit(json[e]))) e++;
        return e > s ? json.Substring(s, e - s) : "";
    }

    static bool GetJar(out string file, out string info, out string err) {
        file = null; info = ""; err = "";
        try {
            string json = Fetch(VersionUrl, 15000);
            string url = Field(json, "url"); string build = Field(json, "build");
            if (url.Length == 0) { err = "version.json has no url"; }
            else {
                string tmp = Path.Combine(Path.GetTempPath(), "zero_loader_" + JarName);
                var req = (System.Net.HttpWebRequest)System.Net.HttpWebRequest.Create(url);
                req.UserAgent = "ZERO-Loader"; req.Timeout = 60000; req.ReadWriteTimeout = 60000;
                using (var resp = (System.Net.HttpWebResponse)req.GetResponse())
                using (var src = resp.GetResponseStream())
                using (var dst = File.Create(tmp)) {
                    var buf = new byte[65536]; int n; long total = 0;
                    while ((n = src.Read(buf, 0, buf.Length)) > 0) { dst.Write(buf, 0, n); total += n; }
                    info = "build " + build + " - " + (total / 1024) + " KB";
                }
                file = tmp; return true;
            }
        } catch (Exception ex) { err = ex.Message; }
        try { string local = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, JarName); if (File.Exists(local)) { file = local; info = "offline copy"; err = ""; return true; } } catch { }
        if (err.Length == 0) err = "could not reach the download server";
        return false;
    }

    // ---------------- CLI

    static int RunCli(string[] args) {
        try {
            if (args[0] == "--check") {
                Reload();
                Console.WriteLine("profiles: " + profiles.Count);
                foreach (var p in profiles) Console.WriteLine("  [" + (p.HasZero ? "x" : " ") + "] " + p.Launcher + " | " + p.Name + " | " + p.Mods);
                string f, i, e; Console.WriteLine(GetJar(out f, out i, out e) ? "jar: OK " + i : "jar: FAIL " + e);
                return 0;
            }
            if (args[0] == "--install" && args.Length > 1) {
                string dir = args[1]; string mods = Path.Combine(dir, "mods");
                string f, i, e;
                if (!GetJar(out f, out i, out e)) { Console.WriteLine("FAILED: " + e); return 1; }
                Directory.CreateDirectory(mods);
                foreach (var old in Directory.GetFiles(mods, "zero-client*.jar")) File.Delete(old);
                File.Copy(f, Path.Combine(mods, JarName), true);
                Console.WriteLine("OK " + i + " -> " + mods);
                return 0;
            }
        } catch (Exception ex) { Console.WriteLine("ERROR " + ex.Message); return 1; }
        Console.WriteLine("usage: --check | --install <gamedir>");
        return 2;
    }
}
