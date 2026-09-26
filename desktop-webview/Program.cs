using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;
using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using System.Text;

namespace HosmimEtHatzir;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        ApplicationConfiguration.Initialize();
        Application.Run(new GameForm());
    }
}

internal sealed class GameForm : Form
{
    private readonly WebView2 webView = new();
    private readonly string appDirectory;
    private LocalGameServer? server;

    public GameForm()
    {
        appDirectory = Path.Combine(AppContext.BaseDirectory, "app");
        Text = "חוסמים את הציר";
        Width = 1280; Height = 800; MinimumSize = new Size(800, 600);
        StartPosition = FormStartPosition.CenterScreen;
        Icon = LoadIcon();
        webView.Dock = DockStyle.Fill;
        Controls.Add(webView);
        Load += async (_, _) => await InitializeWebViewAsync();
        FormClosed += async (_, _) => { webView.Dispose(); if (server != null) await server.DisposeAsync(); };
    }

    private async Task InitializeWebViewAsync()
    {
        Directory.CreateDirectory(appDirectory);
        var userData = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "HosmimEtHatzir", "WebView2");
        Directory.CreateDirectory(userData);
        var options = new CoreWebView2EnvironmentOptions("--autoplay-policy=no-user-gesture-required");
        var environment = await CoreWebView2Environment.CreateAsync(null, userData, options);
        await webView.EnsureCoreWebView2Async(environment);

        webView.CoreWebView2.Settings.AreDevToolsEnabled = false;
        webView.CoreWebView2.Settings.AreDefaultContextMenusEnabled = true;
        webView.CoreWebView2.Settings.IsStatusBarEnabled = false;
        webView.CoreWebView2.Settings.IsZoomControlEnabled = true;

        webView.CoreWebView2.NewWindowRequested += (_, e) =>
        {
            if (Uri.TryCreate(e.Uri, UriKind.Absolute, out var uri) &&
                (uri.Scheme == Uri.UriSchemeHttp || uri.Scheme == Uri.UriSchemeHttps || uri.Scheme == Uri.UriSchemeMailto))
                try { Process.Start(new ProcessStartInfo(uri.AbsoluteUri) { UseShellExecute = true }); } catch { }
            e.Handled = true;
        };

        webView.CoreWebView2.NavigationStarting += (_, e) =>
        {
            if (!IsLocalGameUrl(e.Uri))
            {
                e.Cancel = true;
                try { Process.Start(new ProcessStartInfo(e.Uri) { UseShellExecute = true }); } catch { }
            }
        };

        var index = Path.Combine(appDirectory, "index.html");
        if (!File.Exists(index))
        {
            MessageBox.Show("לא נמצא קובץ המשחק index.html.", "שגיאה", MessageBoxButtons.OK, MessageBoxIcon.Error);
            Close();
            return;
        }

        server = new LocalGameServer(appDirectory);
        var url = await server.StartAsync();
        webView.CoreWebView2.Navigate(url + "index.html");
    }

    private bool IsLocalGameUrl(string url)
    {
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri) || uri.Scheme != Uri.UriSchemeHttp)
            return false;
        return IPAddress.TryParse(uri.Host, out var address) && IPAddress.IsLoopback(address) &&
               server != null && uri.Port == server.Port;
    }

    private Icon? LoadIcon()
    {
        var png = Path.Combine(appDirectory, "icon-512.png");
        return File.Exists(png) ? Icon.FromHandle(new Bitmap(png).GetHicon()) : null;
    }
}

internal sealed class LocalGameServer : IAsyncDisposable
{
    private readonly string root;
    private TcpListener? listener;
    private CancellationTokenSource? cts;
    public int Port { get; private set; }

    public LocalGameServer(string root) =>
        this.root = Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);

    public Task<string> StartAsync()
    {
        cts = new CancellationTokenSource();
        listener = new TcpListener(IPAddress.Loopback, 0);
        listener.Start();
        Port = ((IPEndPoint)listener.LocalEndpoint).Port;
        _ = AcceptLoopAsync(listener, cts.Token);
        return Task.FromResult($"http://127.0.0.1:{Port}/");
    }

    private async Task AcceptLoopAsync(TcpListener tcp, CancellationToken token)
    {
        try
        {
            while (!token.IsCancellationRequested)
            {
                var client = await tcp.AcceptTcpClientAsync(token);
                _ = HandleClientAsync(client, token);
            }
        }
        catch (OperationCanceledException) { }
        catch (ObjectDisposedException) { }
    }

    private async Task HandleClientAsync(TcpClient client, CancellationToken token)
    {
        using (client)
        using (var stream = client.GetStream())
        {
            try
            {
                var requestBuffer = new byte[16384];
                var used = 0;
                while (used < requestBuffer.Length)
                {
                    var n = await stream.ReadAsync(requestBuffer.AsMemory(used, requestBuffer.Length - used), token);
                    if (n == 0) return;
                    used += n;
                    if (used >= 4 && requestBuffer[used-4] == 13 && requestBuffer[used-3] == 10 && requestBuffer[used-2] == 13 && requestBuffer[used-1] == 10) break;
                }

                var request = Encoding.ASCII.GetString(requestBuffer, 0, used);
                var firstLine = request.Split("\r\n", 2)[0];
                var parts = firstLine.Split(' ', 3);
                if (parts.Length < 2) { await SendTextAsync(stream, 400, "Bad Request", "Bad Request", token); return; }

                var method = parts[0];
                if (!string.Equals(method, "GET", StringComparison.OrdinalIgnoreCase) && !string.Equals(method, "HEAD", StringComparison.OrdinalIgnoreCase))
                { await SendTextAsync(stream, 405, "Method Not Allowed", "Method Not Allowed", token); return; }

                var rawTarget = parts[1];
                string path;
                try { path = Uri.UnescapeDataString(rawTarget.Split('?', 2)[0]); }
                catch { await SendTextAsync(stream, 400, "Bad Request", "Bad Request", token); return; }

                if (path == "/" || string.IsNullOrEmpty(path)) path = "/index.html";
                if (path.Contains('\0') || path.Contains(".."))
                { await SendTextAsync(stream, 403, "Forbidden", "Forbidden", token); return; }

                var relative = path.TrimStart('/').Replace('/', Path.DirectorySeparatorChar);
                var file = Path.GetFullPath(Path.Combine(root, relative));
                var rootPrefix = root + Path.DirectorySeparatorChar;
                if (!file.StartsWith(rootPrefix, StringComparison.OrdinalIgnoreCase) && !string.Equals(file, root, StringComparison.OrdinalIgnoreCase))
                { await SendTextAsync(stream, 403, "Forbidden", "Forbidden", token); return; }

                if (!File.Exists(file))
                { await SendTextAsync(stream, 404, "Not Found", "Not Found", token); return; }

                var info = new FileInfo(file);
                long start = 0, end = info.Length - 1;
                var partial = false;
                var range = GetHeader(request, "Range");
                if (!string.IsNullOrWhiteSpace(range) && range.StartsWith("bytes=", StringComparison.OrdinalIgnoreCase))
                {
                    var spec = range["bytes=".Length..].Split(',', 2)[0].Trim();
                    var dash = spec.IndexOf('-');
                    if (dash >= 0)
                    {
                        var left = spec[..dash].Trim();
                        var right = spec[(dash + 1)..].Trim();
                        if (long.TryParse(left, out var parsedStart)) start = Math.Max(0, parsedStart);
                        if (long.TryParse(right, out var parsedEnd)) end = Math.Min(info.Length - 1, parsedEnd);
                        else if (!string.IsNullOrEmpty(left)) end = info.Length - 1;
                        else if (long.TryParse(right, out var suffix)) start = Math.Max(0, info.Length - suffix);
                        if (start <= end && start < info.Length) partial = true;
                    }
                }

                if (start < 0 || start >= info.Length || end < start)
                { await SendTextAsync(stream, 416, "Range Not Satisfiable", "Requested Range Not Satisfiable", token); return; }

                var length = end - start + 1;
                var header = new StringBuilder();
                header.Append(partial ? "HTTP/1.1 206 Partial Content\r\n" : "HTTP/1.1 200 OK\r\n");
                header.Append($"Content-Type: {GetContentType(file)}\r\n");
                header.Append($"Content-Length: {length}\r\n");
                header.Append("Cache-Control: no-cache\r\nAccept-Ranges: bytes\r\n");
                if (partial) header.Append($"Content-Range: bytes {start}-{end}/{info.Length}\r\n");
                header.Append("Connection: close\r\n\r\n");
                await stream.WriteAsync(Encoding.ASCII.GetBytes(header.ToString()), token);
                if (method.Equals("HEAD", StringComparison.OrdinalIgnoreCase)) return;

                await using var fs = new FileStream(file, FileMode.Open, FileAccess.Read, FileShare.Read, 64 * 1024, FileOptions.Asynchronous | FileOptions.SequentialScan);
                fs.Position = start;
                var buffer = new byte[64 * 1024];
                var remaining = length;
                while (remaining > 0)
                {
                    var want = (int)Math.Min(buffer.Length, remaining);
                    var n = await fs.ReadAsync(buffer.AsMemory(0, want), token);
                    if (n <= 0) break;
                    await stream.WriteAsync(buffer.AsMemory(0, n), token);
                    remaining -= n;
                }
            }
            catch (OperationCanceledException) { }
            catch (IOException) { }
            catch (SocketException) { }
        }
    }

    private static string? GetHeader(string request, string name)
    {
        foreach (var line in request.Split("\r\n"))
        {
            var colon = line.IndexOf(':');
            if (colon > 0 && line[..colon].Trim().Equals(name, StringComparison.OrdinalIgnoreCase))
                return line[(colon + 1)..].Trim();
        }
        return null;
    }

    private static async Task SendTextAsync(NetworkStream stream, int code, string reason, string body, CancellationToken token)
    {
        var bytes = Encoding.UTF8.GetBytes(body);
        var header = $"HTTP/1.1 {code} {reason}\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: {bytes.Length}\r\nConnection: close\r\n\r\n";
        await stream.WriteAsync(Encoding.ASCII.GetBytes(header), token);
        await stream.WriteAsync(bytes, token);
    }

    private static string GetContentType(string path) => Path.GetExtension(path).ToLowerInvariant() switch
    {
        ".html" => "text/html; charset=utf-8",
        ".js" => "text/javascript; charset=utf-8",
        ".css" => "text/css; charset=utf-8",
        ".json" => "application/json; charset=utf-8",
        ".png" => "image/png",
        ".jpg" or ".jpeg" => "image/jpeg",
        ".gif" => "image/gif",
        ".svg" => "image/svg+xml",
        ".ico" => "image/x-icon",
        ".mp4" => "video/mp4",
        ".webm" => "video/webm",
        ".woff" => "font/woff",
        ".woff2" => "font/woff2",
        ".ttf" => "font/ttf",
        ".otf" => "font/otf",
        _ => "application/octet-stream"
    };

    public ValueTask DisposeAsync()
    {
        try { cts?.Cancel(); } catch { }
        try { listener?.Stop(); } catch { }
        cts?.Dispose();
        return ValueTask.CompletedTask;
    }
}
