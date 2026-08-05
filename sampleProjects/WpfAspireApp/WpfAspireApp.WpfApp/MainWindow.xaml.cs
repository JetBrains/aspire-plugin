using System.Windows;

namespace WpfAspireApp.WpfApp;

public partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();

        Message.Text = "Hello from WpfApp1";
    }
}
