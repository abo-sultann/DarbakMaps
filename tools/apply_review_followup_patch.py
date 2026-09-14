from pathlib import Path


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"missing pattern: {label}")
    return text.replace(old, new, 1)

main = Path('app/src/main/java/com/abosultan/darbakmaps/MainActivity.java')
s = main.read_text()

marker = 'retain active track until finalize result'
if marker in s:
    print('final UI/state patch already applied')
    raise SystemExit(0)

old = '''    void onBackgroundTrackSettingChanged(boolean enabled) {\n        if (enabled) {\n            if (mapController != null) {\n                mapController.beginTrack();\n                restoreActiveTrack();\n            }\n        } else if (mapController != null) {\n            mapController.beginTrack();\n        }\n        syncBackgroundTrackUi();\n    }'''
new = '''    void onBackgroundTrackSettingChanged(boolean enabled) {\n        if (enabled) {\n            if (mapController != null) {\n                mapController.beginTrack();\n                restoreActiveTrack();\n            }\n        }\n        // retain active track until finalize result: on failure the user must still see\n        // the recoverable path instead of losing the visual context before persistence ends.\n        syncBackgroundTrackUi();\n    }'''
s = replace_once(s, old, new, 'retain track while finalizing')

old = '''            if (!enabled) {\n                NavigationGuidance.stop(this);\n                View nav = findViewById(R.id.nav_panel);\n                if (nav != null) nav.setVisibility(View.GONE);\n            }'''
new = '''            if (!enabled) {\n                NavigationGuidance.stop(this);\n                BacktrackGuidance.stop(this);\n                View nav = findViewById(R.id.nav_panel);\n                if (nav != null) nav.setVisibility(View.GONE);\n            }'''
s = replace_once(s, old, new, 'clear stale backtrack guidance')

old = '''            if (result.success) {\n                toast(result.message);\n            } else {'''
new = '''            if (result.success) {\n                if (mapController != null && !MapUiPreferences.backgroundTrackEnabled(this)) {\n                    mapController.showActiveTrack(java.util.Collections.emptyList());\n                }\n                toast(result.message);\n            } else {'''
s = replace_once(s, old, new, 'clear track only after successful finalize')

main.write_text(s)
print('applied final UI/state patch')
