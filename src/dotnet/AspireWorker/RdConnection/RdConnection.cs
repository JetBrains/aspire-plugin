using JetBrains.Collections.Viewable;
using JetBrains.Rider.Aspire.Worker.Generated;

namespace JetBrains.Rider.Aspire.Worker.RdConnection;

internal sealed class RdConnection
{
    private SingleThreadScheduler? _scheduler;
    private AspireWorkerModel? _model;

    internal void InitializeWithModelAndScheduler(AspireWorkerModel model, SingleThreadScheduler scheduler)
    {
        _model = model;
        _scheduler = scheduler;
    }

    internal async Task<T?> DoWithModel<T>(Func<AspireWorkerModel, T> action)
    {
        if (_scheduler is null || _model is null)
        {
            return default;
        }

        var model = _model;
        var tcs = new TaskCompletionSource<T>(TaskCreationOptions.RunContinuationsAsynchronously);
        _scheduler.Queue(() =>
        {
            try
            {
                tcs.SetResult(action(model));
            }
            catch (Exception ex)
            {
                tcs.SetException(ex);
            }
        });
        return await tcs.Task;
    }

    internal Task DoWithModel(Action<AspireWorkerModel> action) =>
        DoWithModel(model =>
        {
            action(model);
            return 0;
        });
}