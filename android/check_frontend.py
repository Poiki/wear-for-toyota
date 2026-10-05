"""Local cockpit check; requires the debug APK installed on a Wear OS emulator.
Run: python android/check_frontend.py [emulator-5554]
Screenshots stay in ignored notes/. Never sends a vehicle command.
"""
from pathlib import Path
import os
import math
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'notes' / 'frontend-check'
ADB = Path(os.environ['ANDROID_HOME']) / 'platform-tools' / 'adb.exe'
SERIAL = sys.argv[1] if len(sys.argv) > 1 else 'emulator-5554'
PKG = 'com.poiki.toyotawear'


def adb(*args):
    return subprocess.check_output([str(ADB), '-s', SERIAL, *map(str, args)])


def launch(screen='vehicle', locale='en', *extra):
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'am', 'start', '-n', PKG + '/.MainActivity', '--ez', 'demo', 'true',
        '--es', 'preview', screen, '--es', 'locale', locale, *extra)
    time.sleep(1.5)


def dump():
    for _ in range(3):
        output = adb('shell', 'uiautomator', 'dump', '/sdcard/frontend-check.xml')
        if b'dumped to' in output:
            return ET.fromstring(adb('shell', 'cat', '/sdcard/frontend-check.xml'))
        time.sleep(.5)
    raise AssertionError('Emulator did not expose a UI hierarchy')


def button(tree, label):
    for node in tree.iter('node'):
        if node.get('clickable') == 'true' and any(
            label in (child.get('text'), child.get('content-desc')) for child in node.iter('node')
        ):
            return node
    raise AssertionError(f'Button missing: {label}')


def tap(node):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    adb('shell', 'input', 'tap', (x1 + x2) // 2, (y1 + y2) // 2)


def assert_safe_buttons(tree):
    root = tree.find('node')
    x1,y1,x2,y2 = map(int,re.findall(r'\d+',root.get('bounds')))
    cx,cy,radius = (x1+x2)/2,(y1+y2)/2,min(x2-x1,y2-y1)/2
    for node in tree.iter('node'):
        if node.get('clickable') != 'true':
            continue
        left,top,right,bottom = map(int,re.findall(r'\d+',node.get('bounds')))
        w,h = right-left,bottom-top
        if not (60 <= h <= 115 and h <= w <= 300):
            continue
        # Icon buttons and pills: the two end circles must fit inside the round screen.
        bx,by,cap = (left+right)/2,(top+bottom)/2,h/2
        farthest = max(math.hypot(bx-d-cx,by-cy) for d in ((w-h)/2,-(w-h)/2)) + cap
        assert farthest <= radius + 1, ('Button outside round screen',node.get('bounds'),farthest,radius)


def screenshot(name):
    (OUT / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p'))


def texts(tree):
    return [n.get('text') for n in tree.iter('node')]


def check_status():
    for density in (320, 378):
        adb('shell', 'wm', 'density', density)
        for locale in ('en', 'es', 'de', 'fr', 'it', 'pt'):
            folder = 'values' if locale == 'en' else 'values-' + locale
            resources = ET.parse(ROOT / 'android/watch/src/main/res' / folder / 'strings.xml').getroot()
            fuel = resources.find("string[@name='fuel']").text
            warning = resources.find("string[@name='door_open']").text
            for door_open in ('false', 'true'):
                launch('vehicle', locale, '--ez', 'doorOpen', door_open)
                tree = dump()
                assert_safe_buttons(tree)
                if door_open == 'true':
                    icon = next(n for n in tree.iter('node') if n.get('content-desc') == fuel)
                    status = next(n for n in tree.iter('node') if warning in n.get('text', ''))
                    icon_bounds = list(map(int, re.findall(r'\d+', icon.get('bounds'))))
                    status_bounds = list(map(int, re.findall(r'\d+', status.get('bounds'))))
                    assert icon_bounds[3] <= status_bounds[1], 'Door warning covers fuel icon'
                screenshot(f"{'door-open' if door_open == 'true' else 'dashboard'}-{density}-{locale}")
            print(f'OK status + fuel separation: {locale}, {density} dpi', flush=True)


def check_labels():
    labels = [('en','Unlock','Cancel'), ('es','Abrir','Cancelar'), ('de','Entriegeln','Abbrechen'),
              ('fr','Déverrouiller','Annuler'), ('it','Apri','Annulla'), ('pt','Destrancar','Cancelar')]
    for density in (320,378):
        adb('shell','wm','density',density)
        for locale,unlock,cancel in labels:
            launch('garage',locale)
            tree=dump()
            assert 'Corolla Touring Sports - MY24' in texts(tree)
            assert_safe_buttons(tree)
            screenshot(f'garage-{density}-{locale}')
            launch(locale=locale)
            dashboard=dump()
            assert_safe_buttons(dashboard)
            screenshot(f'dashboard-{density}-{locale}')
            tap(button(dashboard,unlock))
            tree=dump()
            assert button(tree,unlock).get('enabled') == 'true'
            assert_safe_buttons(tree)
            screenshot(f'confirm-{density}-{locale}')
            tap(button(tree,cancel))
            assert '43%' in texts(dump())
            adb('shell','input','swipe',360,225,80,225,300)
            controls=dump()
            assert_safe_buttons(controls)
            screenshot(f'controls-{density}-{locale}')
            launch('climate',locale)
            tree=dump()
            assert_safe_buttons(tree)
            screenshot(f'climate-{density}-{locale}')
            tap(button(tree,'+'))
            tree=dump()
            assert any(text.replace(',','.') == '21.5°C' for text in texts(tree))
            screenshot(f'climate-fraction-{density}-{locale}')
            print(f'OK garage + unlock/cancel + controls: {locale}, {density} dpi',flush=True)


if __name__ == '__main__':
    assert SERIAL.startswith('emulator-'), 'Only emulators: real cars must not be used for this check.'
    assert adb('shell', 'getprop', 'ro.kernel.qemu').strip() == b'1'
    OUT.mkdir(parents=True, exist_ok=True)
    timeout = adb('shell', 'settings', 'get', 'system', 'screen_off_timeout').decode().strip()
    adb('shell', 'settings', 'put', 'system', 'screen_off_timeout', '600000')
    try:
        if '--status' in sys.argv:
            check_status()
            sys.exit(0)
        if '--labels' in sys.argv:
            check_labels()
            sys.exit(0)
        for density in (320, 378):  # 227 dp and 192 dp on the 454 px emulator.
            adb('shell', 'wm', 'density', density)
            for locale, climate in [('en', 'Climate'), ('es', 'Climatizador'), ('de', 'Klima'), ('fr', 'Climatisation'), ('it', 'Clima'), ('pt', 'Climatização')]:
                launch(locale=locale)
                tree = dump()
                assert_safe_buttons(tree)
                assert '43%' in texts(tree), (density, locale, texts(tree))
                screenshot(f'dashboard-{density}-{locale}')
                launch('climate', locale)
                tree = dump()
                assert_safe_buttons(tree)
                assert '21°C' in texts(tree), (density, locale, texts(tree))
                assert button(tree, '+').get('enabled') == 'true'
                screenshot(f'climate-{density}-{locale}')
                print(f'OK cockpit + climate: {locale}, {density} dpi', flush=True)
        adb('shell', 'wm', 'density', 320)
        launch('climate')
        tree = dump()
        plus = button(tree, '+')
        tap(plus)
        assert '21.5°C' in texts(dump()), 'Temperature step must be 0.5 °C'
        for _ in range(20):
            tap(plus)
        tree = dump()
        assert '29°C' in texts(tree) and button(tree, '+').get('enabled') == 'false'
        minus = button(tree, '−')
        for _ in range(25):
            tap(minus)
        tree = dump()
        assert '18°C' in texts(tree) and button(tree, '−').get('enabled') == 'false'
        print('OK temperature step + limits', flush=True)
        launch()
        tap(button(dump(), 'Unlock'))
        tree = dump()
        assert 'Unlock the car?' in texts(tree)
        screenshot('confirm-unlock')
        tap(button(tree, 'Cancel'))
        assert 'Locked' in texts(dump()), 'Cancel must leave the car locked'
        print('OK explicit unlock confirmation + cancel', flush=True)
        launch('climate', 'es', '--ez', 'climateRunning', 'true')
        tree = dump()
        assert '+' not in texts(tree) and 'Apagar' in texts(tree)
        screenshot('climate-running')
        launch('vehicle', 'es', '--es', 'busy', 'Actualizando…')
        assert 'Actualizando…' in texts(dump())
        screenshot('refresh')
        launch('vehicle', 'es', '--ez', 'doorOpen', 'true')
        tree=dump()
        assert any('Puerta abierta' in text for text in texts(tree))
        assert_safe_buttons(tree)
        screenshot('door-open')
        print('OK running climate + refresh + open-door warning; no real commands sent', flush=True)
    finally:
        adb('shell', 'wm', 'density', 'reset')
        adb('shell', 'settings', 'put', 'system', 'screen_off_timeout', timeout)
        adb('shell', 'am', 'force-stop', PKG)
