namespace WinFormsAspireApp.WinFormsApp;

internal partial class MainForm
{
    private DataGridView Forecasts = null!;
    private Button RefreshButton = null!;
    private Label Status = null!;

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            _lifetimeCancellation.Dispose();
        }

        base.Dispose(disposing);
    }

    private void InitializeComponent()
    {
        var layout = new TableLayoutPanel();
        var heading = new Label();
        var footer = new TableLayoutPanel();
        var dateColumn = new DataGridViewTextBoxColumn();
        var temperatureColumn = new DataGridViewTextBoxColumn();
        var summaryColumn = new DataGridViewTextBoxColumn();
        Forecasts = new DataGridView();
        RefreshButton = new Button();
        Status = new Label();

        SuspendLayout();
        layout.SuspendLayout();
        footer.SuspendLayout();

        layout.Dock = DockStyle.Fill;
        layout.Padding = new Padding(20);
        layout.ColumnCount = 1;
        layout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
        layout.RowCount = 3;
        layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));
        layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        layout.RowStyles.Add(new RowStyle(SizeType.AutoSize));

        heading.Text = "Weather forecast";
        heading.AutoSize = true;
        heading.Font = new Font(Font.FontFamily, 24);
        heading.Margin = new Padding(0, 0, 0, 16);

        Forecasts.Dock = DockStyle.Fill;
        Forecasts.Margin = Padding.Empty;
        Forecasts.AutoGenerateColumns = false;
        Forecasts.ReadOnly = true;
        Forecasts.AllowUserToAddRows = false;
        Forecasts.AllowUserToDeleteRows = false;
        Forecasts.RowHeadersVisible = false;
        Forecasts.AutoSizeColumnsMode = DataGridViewAutoSizeColumnsMode.Fill;

        dateColumn.HeaderText = "Date";
        dateColumn.DataPropertyName = nameof(WeatherForecast.Date);
        dateColumn.DefaultCellStyle.Format = "yyyy-MM-dd";
        temperatureColumn.HeaderText = "Temperature (°C)";
        temperatureColumn.DataPropertyName = nameof(WeatherForecast.TemperatureC);
        summaryColumn.HeaderText = "Summary";
        summaryColumn.DataPropertyName = nameof(WeatherForecast.Summary);
        Forecasts.Columns.AddRange(dateColumn, temperatureColumn, summaryColumn);

        footer.Dock = DockStyle.Fill;
        footer.AutoSize = true;
        footer.Margin = new Padding(0, 16, 0, 0);
        footer.ColumnCount = 2;
        footer.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
        footer.ColumnStyles.Add(new ColumnStyle(SizeType.AutoSize));
        footer.RowCount = 1;
        footer.RowStyles.Add(new RowStyle(SizeType.AutoSize));

        Status.Dock = DockStyle.Fill;
        Status.TextAlign = ContentAlignment.MiddleLeft;
        Status.Margin = Padding.Empty;

        RefreshButton.Text = "Refresh";
        RefreshButton.AutoSize = true;
        RefreshButton.Padding = new Padding(16, 6, 16, 6);
        RefreshButton.Margin = new Padding(16, 0, 0, 0);
        RefreshButton.Click += RefreshForecast;

        footer.Controls.Add(Status, 0, 0);
        footer.Controls.Add(RefreshButton, 1, 0);
        layout.Controls.Add(heading, 0, 0);
        layout.Controls.Add(Forecasts, 0, 1);
        layout.Controls.Add(footer, 0, 2);
        Controls.Add(layout);

        AutoScaleDimensions = new SizeF(7F, 15F);
        AutoScaleMode = AutoScaleMode.Font;
        ClientSize = new Size(560, 360);
        MinimumSize = new Size(460, 300);
        StartPosition = FormStartPosition.CenterScreen;
        Text = "Aspire Weather";
        Shown += RefreshForecast;

        footer.ResumeLayout(false);
        footer.PerformLayout();
        layout.ResumeLayout(false);
        layout.PerformLayout();
        ResumeLayout(false);
    }
}
