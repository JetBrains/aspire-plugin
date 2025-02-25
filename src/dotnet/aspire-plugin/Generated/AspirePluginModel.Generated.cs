//------------------------------------------------------------------------------
// <auto-generated>
//     This code was generated by a RdGen v1.13.
//
//     Changes to this file may cause incorrect behavior and will be lost if
//     the code is regenerated.
// </auto-generated>
//------------------------------------------------------------------------------
using System;
using System.Linq;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using JetBrains.Annotations;

using JetBrains.Core;
using JetBrains.Diagnostics;
using JetBrains.Collections;
using JetBrains.Collections.Viewable;
using JetBrains.Lifetimes;
using JetBrains.Serialization;
using JetBrains.Rd;
using JetBrains.Rd.Base;
using JetBrains.Rd.Impl;
using JetBrains.Rd.Tasks;
using JetBrains.Rd.Util;
using JetBrains.Rd.Text;


// ReSharper disable RedundantEmptyObjectCreationArgumentList
// ReSharper disable InconsistentNaming
// ReSharper disable RedundantOverflowCheckingContext


namespace JetBrains.Rider.Aspire.Generated
{
  
  
  /// <summary>
  /// <p>Generated from: AspirePluginModel.kt:10</p>
  /// </summary>
  public class AspirePluginModel : RdExtBase
  {
    //fields
    //public fields
    [NotNull] public IRdEndpoint<string, string> GetProjectOutputType => _GetProjectOutputType;
    [NotNull] public IRdCall<StartSessionHostRequest, StartSessionHostResponse> StartSessionHost => _StartSessionHost;
    [NotNull] public IRdCall<StopSessionHostRequest, Unit> StopSessionHost => _StopSessionHost;
    [NotNull] public void UnitTestRunCancelled(string value) => _UnitTestRunCancelled.Fire(value);
    
    //private fields
    [NotNull] private readonly RdCall<string, string> _GetProjectOutputType;
    [NotNull] private readonly RdCall<StartSessionHostRequest, StartSessionHostResponse> _StartSessionHost;
    [NotNull] private readonly RdCall<StopSessionHostRequest, Unit> _StopSessionHost;
    [NotNull] private readonly RdSignal<string> _UnitTestRunCancelled;
    
    //primary constructor
    private AspirePluginModel(
      [NotNull] RdCall<string, string> getProjectOutputType,
      [NotNull] RdCall<StartSessionHostRequest, StartSessionHostResponse> startSessionHost,
      [NotNull] RdCall<StopSessionHostRequest, Unit> stopSessionHost,
      [NotNull] RdSignal<string> unitTestRunCancelled
    )
    {
      if (getProjectOutputType == null) throw new ArgumentNullException("getProjectOutputType");
      if (startSessionHost == null) throw new ArgumentNullException("startSessionHost");
      if (stopSessionHost == null) throw new ArgumentNullException("stopSessionHost");
      if (unitTestRunCancelled == null) throw new ArgumentNullException("unitTestRunCancelled");
      
      _GetProjectOutputType = getProjectOutputType;
      _StartSessionHost = startSessionHost;
      _StopSessionHost = stopSessionHost;
      _UnitTestRunCancelled = unitTestRunCancelled;
      _StartSessionHost.Async = true;
      _StopSessionHost.Async = true;
      _UnitTestRunCancelled.Async = true;
      _GetProjectOutputType.ValueCanBeNull = true;
      BindableChildren.Add(new KeyValuePair<string, object>("getProjectOutputType", _GetProjectOutputType));
      BindableChildren.Add(new KeyValuePair<string, object>("startSessionHost", _StartSessionHost));
      BindableChildren.Add(new KeyValuePair<string, object>("stopSessionHost", _StopSessionHost));
      BindableChildren.Add(new KeyValuePair<string, object>("unitTestRunCancelled", _UnitTestRunCancelled));
    }
    //secondary constructor
    internal AspirePluginModel (
    ) : this (
      new RdCall<string, string>(JetBrains.Rd.Impl.Serializers.ReadString, JetBrains.Rd.Impl.Serializers.WriteString, ReadStringNullable, WriteStringNullable),
      new RdCall<StartSessionHostRequest, StartSessionHostResponse>(StartSessionHostRequest.Read, StartSessionHostRequest.Write, StartSessionHostResponse.Read, StartSessionHostResponse.Write),
      new RdCall<StopSessionHostRequest, Unit>(StopSessionHostRequest.Read, StopSessionHostRequest.Write, JetBrains.Rd.Impl.Serializers.ReadVoid, JetBrains.Rd.Impl.Serializers.WriteVoid),
      new RdSignal<string>(JetBrains.Rd.Impl.Serializers.ReadString, JetBrains.Rd.Impl.Serializers.WriteString)
    ) {}
    //deconstruct trait
    //statics
    
    public static CtxReadDelegate<string> ReadStringNullable = JetBrains.Rd.Impl.Serializers.ReadString.NullableClass();
    
    public static  CtxWriteDelegate<string> WriteStringNullable = JetBrains.Rd.Impl.Serializers.WriteString.NullableClass();
    
    protected override long SerializationHash => -4698860226523395197L;
    
    protected override Action<ISerializers> Register => RegisterDeclaredTypesSerializers;
    public static void RegisterDeclaredTypesSerializers(ISerializers serializers)
    {
      
      serializers.RegisterToplevelOnce(typeof(JetBrains.Rider.Model.IdeRoot), JetBrains.Rider.Model.IdeRoot.RegisterDeclaredTypesSerializers);
    }
    
    
    //constants
    
    //custom body
    //methods
    //equals trait
    //hash code trait
    //pretty print
    public override void Print(PrettyPrinter printer)
    {
      printer.Println("AspirePluginModel (");
      using (printer.IndentCookie()) {
        printer.Print("getProjectOutputType = "); _GetProjectOutputType.PrintEx(printer); printer.Println();
        printer.Print("startSessionHost = "); _StartSessionHost.PrintEx(printer); printer.Println();
        printer.Print("stopSessionHost = "); _StopSessionHost.PrintEx(printer); printer.Println();
        printer.Print("unitTestRunCancelled = "); _UnitTestRunCancelled.PrintEx(printer); printer.Println();
      }
      printer.Print(")");
    }
    //toString
    public override string ToString()
    {
      var printer = new SingleLinePrettyPrinter();
      Print(printer);
      return printer.ToString();
    }
  }
  public static class SolutionAspirePluginModelEx
   {
    public static AspirePluginModel GetAspirePluginModel(this JetBrains.Rider.Model.Solution solution)
    {
      return solution.GetOrCreateExtension("aspirePluginModel", () => new AspirePluginModel());
    }
  }
  
  
  /// <summary>
  /// <p>Generated from: AspirePluginModel.kt:17</p>
  /// </summary>
  public sealed class SessionHostEnvironmentVariable : IPrintable, IEquatable<SessionHostEnvironmentVariable>
  {
    //fields
    //public fields
    [NotNull] public string Key {get; private set;}
    [NotNull] public string Value {get; private set;}
    
    //private fields
    //primary constructor
    public SessionHostEnvironmentVariable(
      [NotNull] string key,
      [NotNull] string value
    )
    {
      if (key == null) throw new ArgumentNullException("key");
      if (value == null) throw new ArgumentNullException("value");
      
      Key = key;
      Value = value;
    }
    //secondary constructor
    //deconstruct trait
    public void Deconstruct([NotNull] out string key, [NotNull] out string value)
    {
      key = Key;
      value = Value;
    }
    //statics
    
    public static CtxReadDelegate<SessionHostEnvironmentVariable> Read = (ctx, reader) => 
    {
      var key = reader.ReadString();
      var value = reader.ReadString();
      var _result = new SessionHostEnvironmentVariable(key, value);
      return _result;
    };
    
    public static CtxWriteDelegate<SessionHostEnvironmentVariable> Write = (ctx, writer, value) => 
    {
      writer.Write(value.Key);
      writer.Write(value.Value);
    };
    
    //constants
    
    //custom body
    //methods
    //equals trait
    public override bool Equals(object obj)
    {
      if (ReferenceEquals(null, obj)) return false;
      if (ReferenceEquals(this, obj)) return true;
      if (obj.GetType() != GetType()) return false;
      return Equals((SessionHostEnvironmentVariable) obj);
    }
    public bool Equals(SessionHostEnvironmentVariable other)
    {
      if (ReferenceEquals(null, other)) return false;
      if (ReferenceEquals(this, other)) return true;
      return Key == other.Key && Value == other.Value;
    }
    //hash code trait
    public override int GetHashCode()
    {
      unchecked {
        var hash = 0;
        hash = hash * 31 + Key.GetHashCode();
        hash = hash * 31 + Value.GetHashCode();
        return hash;
      }
    }
    //pretty print
    public void Print(PrettyPrinter printer)
    {
      printer.Println("SessionHostEnvironmentVariable (");
      using (printer.IndentCookie()) {
        printer.Print("key = "); Key.PrintEx(printer); printer.Println();
        printer.Print("value = "); Value.PrintEx(printer); printer.Println();
      }
      printer.Print(")");
    }
    //toString
    public override string ToString()
    {
      var printer = new SingleLinePrettyPrinter();
      Print(printer);
      return printer.ToString();
    }
  }
  
  
  /// <summary>
  /// <p>Generated from: AspirePluginModel.kt:11</p>
  /// </summary>
  public sealed class StartSessionHostRequest : IPrintable, IEquatable<StartSessionHostRequest>
  {
    //fields
    //public fields
    [NotNull] public string UnitTestRunId {get; private set;}
    [NotNull] public string AspireHostProjectPath {get; private set;}
    public bool UnderDebugger {get; private set;}
    
    //private fields
    //primary constructor
    public StartSessionHostRequest(
      [NotNull] string unitTestRunId,
      [NotNull] string aspireHostProjectPath,
      bool underDebugger
    )
    {
      if (unitTestRunId == null) throw new ArgumentNullException("unitTestRunId");
      if (aspireHostProjectPath == null) throw new ArgumentNullException("aspireHostProjectPath");
      
      UnitTestRunId = unitTestRunId;
      AspireHostProjectPath = aspireHostProjectPath;
      UnderDebugger = underDebugger;
    }
    //secondary constructor
    //deconstruct trait
    public void Deconstruct([NotNull] out string unitTestRunId, [NotNull] out string aspireHostProjectPath, out bool underDebugger)
    {
      unitTestRunId = UnitTestRunId;
      aspireHostProjectPath = AspireHostProjectPath;
      underDebugger = UnderDebugger;
    }
    //statics
    
    public static CtxReadDelegate<StartSessionHostRequest> Read = (ctx, reader) => 
    {
      var unitTestRunId = reader.ReadString();
      var aspireHostProjectPath = reader.ReadString();
      var underDebugger = reader.ReadBool();
      var _result = new StartSessionHostRequest(unitTestRunId, aspireHostProjectPath, underDebugger);
      return _result;
    };
    
    public static CtxWriteDelegate<StartSessionHostRequest> Write = (ctx, writer, value) => 
    {
      writer.Write(value.UnitTestRunId);
      writer.Write(value.AspireHostProjectPath);
      writer.Write(value.UnderDebugger);
    };
    
    //constants
    
    //custom body
    //methods
    //equals trait
    public override bool Equals(object obj)
    {
      if (ReferenceEquals(null, obj)) return false;
      if (ReferenceEquals(this, obj)) return true;
      if (obj.GetType() != GetType()) return false;
      return Equals((StartSessionHostRequest) obj);
    }
    public bool Equals(StartSessionHostRequest other)
    {
      if (ReferenceEquals(null, other)) return false;
      if (ReferenceEquals(this, other)) return true;
      return UnitTestRunId == other.UnitTestRunId && AspireHostProjectPath == other.AspireHostProjectPath && UnderDebugger == other.UnderDebugger;
    }
    //hash code trait
    public override int GetHashCode()
    {
      unchecked {
        var hash = 0;
        hash = hash * 31 + UnitTestRunId.GetHashCode();
        hash = hash * 31 + AspireHostProjectPath.GetHashCode();
        hash = hash * 31 + UnderDebugger.GetHashCode();
        return hash;
      }
    }
    //pretty print
    public void Print(PrettyPrinter printer)
    {
      printer.Println("StartSessionHostRequest (");
      using (printer.IndentCookie()) {
        printer.Print("unitTestRunId = "); UnitTestRunId.PrintEx(printer); printer.Println();
        printer.Print("aspireHostProjectPath = "); AspireHostProjectPath.PrintEx(printer); printer.Println();
        printer.Print("underDebugger = "); UnderDebugger.PrintEx(printer); printer.Println();
      }
      printer.Print(")");
    }
    //toString
    public override string ToString()
    {
      var printer = new SingleLinePrettyPrinter();
      Print(printer);
      return printer.ToString();
    }
  }
  
  
  /// <summary>
  /// <p>Generated from: AspirePluginModel.kt:22</p>
  /// </summary>
  public sealed class StartSessionHostResponse : IPrintable, IEquatable<StartSessionHostResponse>
  {
    //fields
    //public fields
    [NotNull] public SessionHostEnvironmentVariable[] EnvironmentVariables {get; private set;}
    
    //private fields
    //primary constructor
    public StartSessionHostResponse(
      [NotNull] SessionHostEnvironmentVariable[] environmentVariables
    )
    {
      if (environmentVariables == null) throw new ArgumentNullException("environmentVariables");
      
      EnvironmentVariables = environmentVariables;
    }
    //secondary constructor
    //deconstruct trait
    public void Deconstruct([NotNull] out SessionHostEnvironmentVariable[] environmentVariables)
    {
      environmentVariables = EnvironmentVariables;
    }
    //statics
    
    public static CtxReadDelegate<StartSessionHostResponse> Read = (ctx, reader) => 
    {
      var environmentVariables = ReadSessionHostEnvironmentVariableArray(ctx, reader);
      var _result = new StartSessionHostResponse(environmentVariables);
      return _result;
    };
    public static CtxReadDelegate<SessionHostEnvironmentVariable[]> ReadSessionHostEnvironmentVariableArray = SessionHostEnvironmentVariable.Read.Array();
    
    public static CtxWriteDelegate<StartSessionHostResponse> Write = (ctx, writer, value) => 
    {
      WriteSessionHostEnvironmentVariableArray(ctx, writer, value.EnvironmentVariables);
    };
    public static  CtxWriteDelegate<SessionHostEnvironmentVariable[]> WriteSessionHostEnvironmentVariableArray = SessionHostEnvironmentVariable.Write.Array();
    
    //constants
    
    //custom body
    //methods
    //equals trait
    public override bool Equals(object obj)
    {
      if (ReferenceEquals(null, obj)) return false;
      if (ReferenceEquals(this, obj)) return true;
      if (obj.GetType() != GetType()) return false;
      return Equals((StartSessionHostResponse) obj);
    }
    public bool Equals(StartSessionHostResponse other)
    {
      if (ReferenceEquals(null, other)) return false;
      if (ReferenceEquals(this, other)) return true;
      return EnvironmentVariables.SequenceEqual(other.EnvironmentVariables);
    }
    //hash code trait
    public override int GetHashCode()
    {
      unchecked {
        var hash = 0;
        hash = hash * 31 + EnvironmentVariables.ContentHashCode();
        return hash;
      }
    }
    //pretty print
    public void Print(PrettyPrinter printer)
    {
      printer.Println("StartSessionHostResponse (");
      using (printer.IndentCookie()) {
        printer.Print("environmentVariables = "); EnvironmentVariables.PrintEx(printer); printer.Println();
      }
      printer.Print(")");
    }
    //toString
    public override string ToString()
    {
      var printer = new SingleLinePrettyPrinter();
      Print(printer);
      return printer.ToString();
    }
  }
  
  
  /// <summary>
  /// <p>Generated from: AspirePluginModel.kt:26</p>
  /// </summary>
  public sealed class StopSessionHostRequest : IPrintable, IEquatable<StopSessionHostRequest>
  {
    //fields
    //public fields
    [NotNull] public string UnitTestRunId {get; private set;}
    
    //private fields
    //primary constructor
    public StopSessionHostRequest(
      [NotNull] string unitTestRunId
    )
    {
      if (unitTestRunId == null) throw new ArgumentNullException("unitTestRunId");
      
      UnitTestRunId = unitTestRunId;
    }
    //secondary constructor
    //deconstruct trait
    public void Deconstruct([NotNull] out string unitTestRunId)
    {
      unitTestRunId = UnitTestRunId;
    }
    //statics
    
    public static CtxReadDelegate<StopSessionHostRequest> Read = (ctx, reader) => 
    {
      var unitTestRunId = reader.ReadString();
      var _result = new StopSessionHostRequest(unitTestRunId);
      return _result;
    };
    
    public static CtxWriteDelegate<StopSessionHostRequest> Write = (ctx, writer, value) => 
    {
      writer.Write(value.UnitTestRunId);
    };
    
    //constants
    
    //custom body
    //methods
    //equals trait
    public override bool Equals(object obj)
    {
      if (ReferenceEquals(null, obj)) return false;
      if (ReferenceEquals(this, obj)) return true;
      if (obj.GetType() != GetType()) return false;
      return Equals((StopSessionHostRequest) obj);
    }
    public bool Equals(StopSessionHostRequest other)
    {
      if (ReferenceEquals(null, other)) return false;
      if (ReferenceEquals(this, other)) return true;
      return UnitTestRunId == other.UnitTestRunId;
    }
    //hash code trait
    public override int GetHashCode()
    {
      unchecked {
        var hash = 0;
        hash = hash * 31 + UnitTestRunId.GetHashCode();
        return hash;
      }
    }
    //pretty print
    public void Print(PrettyPrinter printer)
    {
      printer.Println("StopSessionHostRequest (");
      using (printer.IndentCookie()) {
        printer.Print("unitTestRunId = "); UnitTestRunId.PrintEx(printer); printer.Println();
      }
      printer.Print(")");
    }
    //toString
    public override string ToString()
    {
      var printer = new SingleLinePrettyPrinter();
      Print(printer);
      return printer.ToString();
    }
  }
}
