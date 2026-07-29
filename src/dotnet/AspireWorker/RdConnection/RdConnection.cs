using JetBrains.Collections.Viewable;
using JetBrains.Lifetimes;
using JetBrains.Rider.Aspire.Worker.Generated;

namespace JetBrains.Rider.Aspire.Worker.RdConnection;

internal sealed class RdConnection
{
    private SingleThreadScheduler? _scheduler;
    private AspireWorkerModel? _model;
    private Lifetime? _lifetime;

    internal void InitializeWithModelAndScheduler(AspireWorkerModel model, SingleThreadScheduler scheduler, Lifetime lifetime)
    {
        _model = model;
        _scheduler = scheduler;
        _lifetime = lifetime;
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

    internal async Task<T?> DoWithModelAsync<T>(Func<AspireWorkerModel, Lifetime, Task<T>> action)
    {
        if (_scheduler is null || _model is null || _lifetime is null)
        {
            return default;
        }

        var model = _model;
        var lifetime = _lifetime.Value;
        var tcs = new TaskCompletionSource<Task<T>>(TaskCreationOptions.RunContinuationsAsynchronously);
        _scheduler.Queue(() =>
        {
            try
            {
                tcs.SetResult(action(model, lifetime));
            }
            catch (Exception ex)
            {
                tcs.SetException(ex);
            }
        });

        var responseTask = await tcs.Task;
        return await responseTask;
    }

    internal Task DoWithModel(Action<AspireWorkerModel> action) =>
        DoWithModel(model =>
        {
            action(model);
            return 0;
        });
}