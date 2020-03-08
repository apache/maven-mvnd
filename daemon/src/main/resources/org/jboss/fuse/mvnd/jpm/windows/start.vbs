'===============================================================================
'
'    Licensed to the Apache Software Foundation (ASF) under one or more
'    contributor license agreements.  See the NOTICE file distributed with
'    this work for additional information regarding copyright ownership.
'    The ASF licenses this file to You under the Apache License, Version 2.0
'    (the "License"); you may not use this file except in compliance with
'    the License.  You may obtain a copy of the License at
'
'       http://www.apache.org/licenses/LICENSE-2.0
'
'    Unless required by applicable law or agreed to in writing, software
'    distributed under the License is distributed on an "AS IS" BASIS,
'    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
'    See the License for the specific language governing permissions and
'    limitations under the License.
'
'===============================================================================

Set objWMIService = GetObject("winmgmts:\\.\root\cimv2")
Set objConfig = objWMIService.Get("Win32_ProcessStartup").SpawnInstance_
objConfig.ShowWindow = SW_HIDE
objConfig.CreateFlags = 8
If Len("${dir}") > 0 Then
    intReturn = objWMIService.Get("Win32_Process").Create("${command}", "${dir}", objConfig, intProcessID)
Else
    intReturn = objWMIService.Get("Win32_Process").Create("${command}", Null, objConfig, intProcessID)
End If
If intReturn = 0 Then
    Set objOutputFile = CreateObject("Scripting.fileSystemObject").CreateTextFile("${pid.file}", TRUE)
    objOutputFile.WriteLine(intProcessID)
    objOutputFile.Close
End If
WScript.Quit(intReturn)
