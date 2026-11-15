"""Small static checks, not a substitute for Android Lint or platform review."""
from pathlib import Path
from xml.etree import ElementTree as ET
import hashlib

root = Path(__file__).resolve().parents[1]
ns = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
permissions = {p.get(ns + 'name') for p in manifest.findall('uses-permission')}
assert permissions == {
    'pleos.car.permission.CAR_ENERGY',
    'pleos.car.permission.NAVI_ROUTE',
    'pleos.car.permission.NAVI_ROUTE_SEARCH',
}, permissions
assert manifest.find('application').get(ns + 'allowBackup') == 'false'
assert not manifest.findall('.//meta-data[@' + ns + 'name="distractionOptimized"]')
sources = '\n'.join(p.read_text() for p in (root/'app/src/main/kotlin').rglob('*.kt'))
assert '.getGear(' not in sources and 'WebView(' not in sources
assert sources.count('h.addWaypoint(') == 1, 'Keep one auditable route-write boundary'
assert 'requestRoute(' not in sources and 'cancelRoute(' not in sources
assert hashlib.sha256((root/'gradle/wrapper/gradle-wrapper.jar').read_bytes()).hexdigest() == '2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046'
print('PASS: three source-declared PLEOS permissions (SDK adds binding permission in APK), backup disabled, no DO declaration, no Gear/WebView, one route-write boundary, official wrapper checksum.')
