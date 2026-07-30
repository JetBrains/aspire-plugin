using System.Windows;

namespace WpfApp1;

public partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();

        Message.Text = "Hello from WpfApp1";
    }
}
