using AspireSessionHost.Generated;
using JetBrains.Core;
using JetBrains.Rd.Tasks;

namespace AspireSessionHost.Sessions;

internal sealed class SessionNodeService(Connection connection)
{
    private readonly Dictionary<string, Node> _nodes = new();
    private readonly Dictionary<string, string> _spanIdToNodeIdMap = new();
    private readonly Dictionary<string, List<string>> _orphanConnections = new();

    internal async Task Initialize()
    {
        await connection.DoWithModel(model => { model.GetTraceNodes.SetSync(GetAllNodes); });
    }

    private TraceNode[] GetAllNodes(Unit unit) => _nodes.Values.Select(it => new TraceNode(
        it.NodeId,
        it.Name,
        it.ServiceName,
        it.Children.Select(ch => new TraceNodeChild(ch.NodeId, ch.ConnectionCount)).ToList(),
        it.Attributes.Select(pair => new TraceNodeAttribute(pair.Key, pair.Value)).ToList()
    )).ToArray();

    internal void ReportTrace(
        string nodeId,
        string name,
        string serviceName,
        string spanId,
        string parentSpanId,
        Dictionary<string, string> attributes)
    {
        _spanIdToNodeIdMap[spanId] = nodeId;

        var parentNodeId = _spanIdToNodeIdMap.GetValueOrDefault(parentSpanId);

        if (!_nodes.TryGetValue(nodeId, out var node))
        {
            var children = new List<NodeConnection>();
            node = new Node(nodeId, serviceName, name, children, attributes);
            _nodes.TryAdd(nodeId, node);
        }

        UpdateNodeConnectionsFromOrphan(spanId, node);

        if (parentNodeId != null)
        {
            UpdateParentNodeConnections(parentNodeId, nodeId);
        }
        else
        {
            UpdateOrphanConnections(parentSpanId, nodeId);
        }
    }

    private void UpdateNodeConnectionsFromOrphan(string spanId, Node node)
    {
        if (_orphanConnections.Remove(spanId, out var childrenNodeIds))
        {
            foreach (var childNodeId in childrenNodeIds)
            {
                var child = node.Children.FirstOrDefault(it => it.NodeId == childNodeId);
                if (child != null)
                {
                    child.ConnectionCount++;
                }
                else
                {
                    node.Children.Add(new NodeConnection(childNodeId) { ConnectionCount = 1 });
                }
            }
        }
    }

    private void UpdateParentNodeConnections(string parentNodeId, string childNodeId)
    {
        var parentNode = _nodes[parentNodeId];
        var parentConnection = parentNode.Children.FirstOrDefault(it => it.NodeId == childNodeId);
        if (parentConnection != null)
        {
            parentConnection.ConnectionCount++;
        }
        else
        {
            parentNode.Children.Add(new NodeConnection(childNodeId) { ConnectionCount = 1 });
        }
    }

    private void UpdateOrphanConnections(string parentSpanId, string childNodeId)
    {
        if (_orphanConnections.TryGetValue(parentSpanId, out var nodeIds))
        {
            nodeIds.Add(childNodeId);
        }
        else
        {
            _orphanConnections.TryAdd(parentSpanId, [childNodeId]);
        }
    }

    private record Node(
        string NodeId,
        string? ServiceName,
        string Name,
        List<NodeConnection> Children,
        Dictionary<string, string> Attributes
    );

    private record NodeConnection(string NodeId)
    {
        internal int ConnectionCount { get; set; }
    }
}