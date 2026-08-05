using System.Windows;

namespace WpfAspireApp.WpfApp;

public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        Console.WriteLine("This is a line from console output");
    }
}
