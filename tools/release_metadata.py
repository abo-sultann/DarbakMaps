import hashlib,json,re,sys
from pathlib import Path
apk,badging,commit=sys.argv[1:]
s=Path(badging).read_text()
p=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",s)
assert p,'APK identity missing'
package,code,name=p.groups();sdk=int(re.search(r"sdkVersion:'(\d+)'",s)[1])
assert package=='com.abosultan.darbakmaps.debug' and sdk<=25
name=name.removesuffix('-debug')
Path('release-metadata.json').write_text(json.dumps(dict(versionCode=int(code),versionName=name,packageName=package,minSdk=sdk,sha256=hashlib.sha256(Path(apk).read_bytes()).hexdigest(),sourceCommit=commit),indent=2)+'\n')
