; AI QA Test Studio v4 — Inno Setup (PyInstaller onedir)
#ifndef MyAppVersion
#define MyAppVersion "4.1.6"
#endif
#ifndef MyAppPublisher
#define MyAppPublisher "Manh Quoc"
#endif
#ifndef MyAppURL
#define MyAppURL "https://github.com/manhquoc11"
#endif

#define MyAppName "AI QA Test Studio"
#define MyAppExeName "AIQATestStudio.exe"
#define MyAppSupportDir "AIQATestStudio"

[Setup]
AppId={{A1QA7E57-5TUD-10V4-B001-AIQATESTSTUDIO}}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
DefaultDirName={autopf}\{#MyAppSupportDir}
DefaultGroupName={#MyAppName}
UsePreviousAppDir=yes
DirExistsWarning=no
CloseApplications=yes
CloseApplicationsFilter=*.exe
AllowNoIcons=yes
OutputDir=..\dist
OutputBaseFilename=AIQATestStudio-{#MyAppVersion}-windows-x64-Setup
SetupIconFile=..\assets\icon.ico
; Inno signs the Setup and the generated uninstaller only when a sign tool is
; supplied (`/DSignedBuild /Saiqa=...`). Without it the build stays unsigned.
#ifdef SignedBuild
SignTool=aiqa
SignedUninstaller=yes
#endif
UninstallDisplayIcon={app}\{#MyAppExeName}
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
PrivilegesRequiredOverridesAllowed=dialog
VersionInfoVersion={#MyAppVersion}
VersionInfoProductVersion={#MyAppVersion}
VersionInfoCompany={#MyAppPublisher}
VersionInfoDescription={#MyAppName} Setup

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "..\dist\AIQATestStudio\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\{#MyAppExeName}"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Registry]
Root: HKA; Subkey: "Software\{#MyAppSupportDir}"; ValueType: string; ValueName: "CurrentVersion"; ValueData: "{#MyAppVersion}"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\{#MyAppSupportDir}"; ValueType: string; ValueName: "InstallLocation"; ValueData: "{app}"; Flags: uninsdeletekeyifempty
Root: HKA; Subkey: "Software\{#MyAppSupportDir}"; ValueType: string; ValueName: "Publisher"; ValueData: "{#MyAppPublisher}"; Flags: uninsdeletekeyifempty

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[Code]
var
  DeleteUserData: Boolean;

function InitializeUninstall(): Boolean;
var
  Prompt: Integer;
begin
  Result := True;
  DeleteUserData := False;
  Prompt := MsgBox(
    'Xóa dữ liệu người dùng và Chromium đã tải (%LOCALAPPDATA%\{#MyAppSupportDir})?',
    mbConfirmation, MB_YESNOCANCEL);
  if Prompt = IDCANCEL then
  begin
    Result := False;
    Exit;
  end;
  DeleteUserData := (Prompt = IDYES);
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  UserData: String;
begin
  if (CurUninstallStep = usPostUninstall) and DeleteUserData then
  begin
    UserData := ExpandConstant('{localappdata}\{#MyAppSupportDir}');
    if DirExists(UserData) then
      DelTree(UserData, True, True, True);
  end;
end;

function IsAppRunning(const FileName: String): Boolean;
var
  ResultCode: Integer;
begin
  Result := Exec(ExpandConstant('{cmd}'),
    '/c tasklist /FI "IMAGENAME eq ' + FileName + '" | find /I "' + FileName + '"',
    '', SW_HIDE, ewWaitUntilTerminated, ResultCode) and (ResultCode = 0);
end;

function InitializeSetup(): Boolean;
var
  PromptResult: Integer;
begin
  Result := True;
  if IsAppRunning('{#MyAppExeName}') then
  begin
    if not WizardSilent then
    begin
      PromptResult := MsgBox(
        '{#MyAppName} đang chạy. OK để đóng ứng dụng và tiếp tục, Cancel để thoát.',
        mbConfirmation, MB_OKCANCEL);
      if PromptResult = IDCANCEL then
      begin
        Result := False;
        Exit;
      end;
    end;
    Exec(ExpandConstant('{sys}\taskkill.exe'), '/F /IM {#MyAppExeName} /T', '', SW_HIDE, ewWaitUntilTerminated, PromptResult);
    Sleep(1000);
  end;
end;
