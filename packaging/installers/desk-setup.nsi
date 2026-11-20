; Desk Windows GUI installer (NSIS).
; Stage inputs with packaging/installers/build-windows-installer.ps1 first.
;
; Expected under DESK_INPUT:
;   desk.exe (optional), portable\ (optional), LICENSE, README.txt

!ifndef DESK_VERSION
  !define DESK_VERSION "0.1.0"
!endif
!ifndef DESK_INPUT
  !define DESK_INPUT "..\..\dist\installer-input"
!endif
!ifndef OUTFILE
  !define OUTFILE "..\..\dist\Desk-Setup-${DESK_VERSION}.exe"
!endif

Name "Desk ${DESK_VERSION}"
OutFile "${OUTFILE}"
InstallDir "$LOCALAPPDATA\Desk"
InstallDirRegKey HKCU "Software\Desk" "InstallDir"
RequestExecutionLevel user
Unicode true
SetCompressor /SOLID lzma

!include "MUI2.nsh"
!include "WinMessages.nsh"

!define MUI_ABORTWARNING

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "${DESK_INPUT}\LICENSE"
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"

!system 'if not exist "${DESK_INPUT}\desk.exe" echo. > "${DESK_INPUT}\.no-exe"'
!system 'if not exist "${DESK_INPUT}\portable" mkdir "${DESK_INPUT}\portable" & echo. > "${DESK_INPUT}\portable\.keep"'

Function AddToUserPath
  ReadRegStr $1 HKCU "Environment" "Path"
  StrCmp $1 "" empty_path
  Push "$1"
  Push "$INSTDIR\bin"
  Call StrStr
  Pop $0
  StrCmp $0 "" append_path already_there
empty_path:
  WriteRegExpandStr HKCU "Environment" "Path" "$INSTDIR\bin"
  Goto notify_env
append_path:
  WriteRegExpandStr HKCU "Environment" "Path" "$1;$INSTDIR\bin"
  Goto notify_env
already_there:
  Return
notify_env:
  SendMessage ${HWND_BROADCAST} ${WM_SETTINGCHANGE} 0 "STR:Environment" /TIMEOUT=3000
FunctionEnd

Function un.RemoveFromUserPath
  ReadRegStr $1 HKCU "Environment" "Path"
  StrCmp $1 "" done
  Push "$1"
  Push ";$INSTDIR\bin"
  Call un.StrRep
  Pop $1
  Push "$1"
  Push "$INSTDIR\bin;"
  Call un.StrRep
  Pop $1
  Push "$1"
  Push "$INSTDIR\bin"
  Call un.StrRep
  Pop $1
  WriteRegExpandStr HKCU "Environment" "Path" "$1"
  SendMessage ${HWND_BROADCAST} ${WM_SETTINGCHANGE} 0 "STR:Environment" /TIMEOUT=3000
done:
FunctionEnd

Function StrStr
  Exch $R1
  Exch
  Exch $R2
  Push $R3
  Push $R4
  Push $R5
  StrLen $R3 $R1
  StrCpy $R4 0
loop:
  StrCpy $R5 $R2 $R3 $R4
  StrCmp $R5 $R1 found
  StrCmp $R5 "" done
  IntOp $R4 $R4 + 1
  Goto loop
found:
  StrCpy $R1 $R2 "" $R4
done:
  Pop $R5
  Pop $R4
  Pop $R3
  Pop $R2
  Exch $R1
FunctionEnd

!macro StrRep un
Function ${un}StrRep
  Exch $R2
  Exch 1
  Exch $R1
  Push $R3
  Push $R4
  Push $R5
  Push $R6
  Push $R7
  Push $R8
  StrCpy $R3 0
  StrLen $R4 $R2
loop:
  StrCpy $R5 $R1 $R4 $R3
  StrCmp $R5 $R2 replace
  StrCmp $R5 "" done
  IntOp $R3 $R3 + 1
  Goto loop
replace:
  StrCpy $R7 $R1 $R3
  IntOp $R8 $R3 + $R4
  StrCpy $R8 $R1 "" $R8
  StrCpy $R1 "$R7$R8"
  Goto loop
done:
  StrCpy $R2 $R1
  Pop $R8
  Pop $R7
  Pop $R6
  Pop $R5
  Pop $R4
  Pop $R3
  Pop $R1
  Exch $R2
FunctionEnd
!macroend
!insertmacro StrRep ""
!insertmacro StrRep "un."

Section "Desk application" SecApp
  SectionIn RO
  SetOutPath "$INSTDIR"

  ; Embed entire staged tree (portable runtime, exe, bin\desk.cmd, docs)
  File /r "${DESK_INPUT}\*.*"

  ; Remove staging placeholders if present
  Delete "$INSTDIR\.no-exe"
  Delete "$INSTDIR\portable\.keep"

  WriteRegStr HKCU "Software\Desk" "InstallDir" "$INSTDIR"
  WriteUninstaller "$INSTDIR\Uninstall.exe"

  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Desk" "DisplayName" "Desk"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Desk" "DisplayVersion" "${DESK_VERSION}"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Desk" "Publisher" "Desk"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Desk" "UninstallString" "$\"$INSTDIR\Uninstall.exe$\""
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Desk" "InstallLocation" "$INSTDIR"
SectionEnd

Section "Start Menu shortcut" SecStart
  CreateDirectory "$SMPROGRAMS\Desk"
  CreateShortCut "$SMPROGRAMS\Desk\Desk.lnk" "$INSTDIR\bin\desk.cmd"
  CreateShortCut "$SMPROGRAMS\Desk\Uninstall Desk.lnk" "$INSTDIR\Uninstall.exe"
SectionEnd

Section "Add Desk to user PATH" SecPath
  Call AddToUserPath
SectionEnd

Section "Uninstall"
  Call un.RemoveFromUserPath

  Delete "$SMPROGRAMS\Desk\Desk.lnk"
  Delete "$SMPROGRAMS\Desk\Uninstall Desk.lnk"
  RMDir "$SMPROGRAMS\Desk"

  Delete "$INSTDIR\Uninstall.exe"
  Delete "$INSTDIR\desk.exe"
  Delete "$INSTDIR\README.txt"
  Delete "$INSTDIR\LICENSE"
  Delete "$INSTDIR\bin\desk.cmd"
  RMDir /r "$INSTDIR\portable"
  RMDir "$INSTDIR\bin"
  RMDir "$INSTDIR"

  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\Desk"
  DeleteRegKey HKCU "Software\Desk"
SectionEnd

LangString DESC_SecApp ${LANG_ENGLISH} "Desk application files and uninstaller."
LangString DESC_SecStart ${LANG_ENGLISH} "Create shortcuts under the Start Menu."
LangString DESC_SecPath ${LANG_ENGLISH} "Append Desk\bin to your user PATH so desk works in new terminals."

!insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
  !insertmacro MUI_DESCRIPTION_TEXT ${SecApp} $(DESC_SecApp)
  !insertmacro MUI_DESCRIPTION_TEXT ${SecStart} $(DESC_SecStart)
  !insertmacro MUI_DESCRIPTION_TEXT ${SecPath} $(DESC_SecPath)
!insertmacro MUI_FUNCTION_DESCRIPTION_END
