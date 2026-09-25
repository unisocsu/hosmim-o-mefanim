using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.WinForms;
using System.Diagnostics;

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

    public GameForm()
    {
        appDirectory = Path.Combine(AppContext.BaseDirectory, "app");

        Text = "חוסמים את הציר";
        Width = 1280;
        Height = 800;
        MinimumSize = new Size(800, 600);
        StartPosition = FormStartPosition.CenterScreen;
        Icon = LoadIcon();

        webView.Dock = DockStyle.Fill;
        Controls.Add(webView);

        Load += async (_, _) => await InitializeWebViewAsync();
        FormClosed += (_, _) => webView.Dispose();
    }

    private async Task InitializeWebViewAsync()
    {
        Directory.CreateDirectory(appDirectory);

        var userData = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "HosmimEtHatzir",
            "WebView2");

        Directory.CreateDirectory(userData);

        var options = new CoreWebView2EnvironmentOptions(
            "--autoplay-policy=no-user-gesture-required");

        var environment = await CoreWebView2Environment.CreateAsync(
            browserExecutableFolder: null,
            userDataFolder: userData,
            options: options);

        await webView.EnsureCoreWebView2Async(environment);

        webView.CoreWebView2.Settings.AreDevToolsEnabled = false;
        webView.CoreWebView2.Settings.AreDefaultContextMenusEnabled = true;
        webView.CoreWebView2.Settings.IsStatusBarEnabled = false;
        webView.CoreWebView2.Settings.IsZoomControlEnabled = true;

        webView.CoreWebView2.NewWindowRequested += (_, e) =>
        {
            if (Uri.TryCreate(e.Uri, UriKind.Absolute, out var uri) &&
                (uri.Scheme == Uri.UriSchemeHttp ||
                 uri.Scheme == Uri.UriSchemeHttps ||
                 uri.Scheme == Uri.UriSchemeMailto))
            {
                try { Process.Start(new ProcessStartInfo(uri.AbsoluteUri) { UseShellExecute = true }); }
                catch { }
            }
            e.Handled = true;
        };

        webView.CoreWebView2.NavigationStarting += (_, e) =>
        {
            if (!IsLocalGameUrl(e.Uri))
            {
                e.Cancel = true;
                try { Process.Start(new ProcessStartInfo(e.Uri) { UseShellExecute = true }); }
                catch { }
            }
        };

        var index = Path.Combine(appDirectory, "index.html");
        if (!File.Exists(index))
        {
            MessageBox.Show("לא נמצא קובץ המשחק index.html.", "שגיאה", MessageBoxButtons.OK, MessageBoxIcon.Error);
            Close();
            return;
        }

        webView.CoreWebView2.Navigate(new Uri(index).AbsoluteUri);
    }

    private bool IsLocalGameUrl(string url)
    {
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri))
            return false;

        if (!uri.IsFile)
            return false;

        var root = Path.GetFullPath(appDirectory)
            .TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;

        var target = Path.GetFullPath(uri.LocalPath);
        return target.StartsWith(root, StringComparison.OrdinalIgnoreCase);
    }

    private Icon? LoadIcon()
    {
        var png = Path.Combine(appDirectory, "icon-512.png");
        return File.Exists(png) ? Icon.FromHandle(new Bitmap(png).GetHicon()) : null;
    }
}
