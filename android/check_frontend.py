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


def check_climate_options():
    def reveal(label):
        for _ in range(24):
            tree = dump()
            try:
                node = button(tree, label)
            except AssertionError:
                adb('shell', 'input', 'swipe', 227, 320, 227, 170, 300)
                continue
            _, top, _, bottom = map(int, re.findall(r'\d+', node.get('bounds')))
            delta = int(227 - (top + bottom) / 2)
            if 40 <= top and bottom <= 414:
                return node
            adb('shell', 'input', 'swipe', 227, 227, 227, 227 + max(-150, min(150, delta)), 300)
        raise AssertionError(('Climate control not reachable', label, texts(dump())))

    for density in (320, 378):
        adb('shell', 'wm', 'density', density)
        for locale in ('en', 'es', 'de', 'fr', 'it', 'pt'):
            folder = 'values' if locale == 'en' else 'values-' + locale
            resources = ET.parse(ROOT / 'android/watch/src/main/res' / folder / 'strings.xml').getroot()
            def label(key):
                return resources.find(f"string[@name='{key}']").text
            launch('climate', locale)
            tree = dump()
            assert '21°C' in texts(tree)
            assert_safe_buttons(tree)
            screenshot(f'climate-extras-entry-{density}-{locale}')
            tap(button(tree, label('climate_options')))
            for key in ('climate_front', 'climate_rear', 'climate_wheel', 'climate_driver',
                        'climate_passenger', 'climate_rear_driver', 'climate_rear_passenger'):
                node = reveal(label(key))
                left, top, right, bottom = map(int, re.findall(r'\d+', node.get('bounds')))
                assert 30 <= top < bottom <= 424, ('Climate button clipped', locale, key, node.get('bounds'))
                for child in node.iter('node'):
                    if child.get('text'):
                        x1,y1,x2,y2 = map(int,re.findall(r'\d+',child.get('bounds')))
                        assert all(math.hypot(x-227,y-227) < 227 for x in (x1,x2) for y in (y1,y2)), ('Climate text outside round screen', locale, child.attrib)
                if key == 'climate_driver':
                    tap(node)
                    selected = label('climate_selected').replace('%1$s', label('climate_heat'))
                    assert selected in [c.get('text') for c in button(dump(), label(key)).iter('node')]
                    tap(button(dump(), label(key)))
                    selected = label('climate_selected').replace('%1$s', label('climate_ventilate'))
                    assert selected in [c.get('text') for c in button(dump(), label(key)).iter('node')]
                    screenshot(f'climate-extras-seats-{density}-{locale}')
            assert reveal(label('climate_apply')).get('enabled') == 'true'
            screenshot(f'climate-extras-apply-{density}-{locale}')
            adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
            assert '21°C' in texts(dump()), 'Options back must return to the temperature dial'
            print(f'OK climate extras + seat modes + centered labels + back: {locale}, {density} dpi; no commands sent', flush=True)


def check_trips(densities=(320, 378), locales=None):
    labels = [('en','Trips','Trip detail'), ('es','Viajes','Detalle del viaje'),
              ('de','Fahrten','Fahrtdetails'), ('fr','Trajets','Détail du trajet'),
              ('it','Viaggi','Dettaglio viaggio'), ('pt','Viagens','Detalhes da viagem')]
    if locales is not None:
        labels = [row for row in labels if row[0] in locales]
    for density in densities:
        adb('shell','wm','density',density)
        for locale,title,detail in labels:
            folder = 'values' if locale == 'en' else 'values-' + locale
            resources = ET.parse(ROOT / 'android/watch/src/main/res' / folder / 'strings.xml').getroot()
            trend = resources.find("string[@name='trips_trend']").text
            chart_hint = resources.find("string[@name='trips_chart_hint']").text
            chart_missing = resources.find("string[@name='trips_chart_missing']").text
            ev_label = resources.find("string[@name='trips_ev_share']").text
            launch('vehicle',locale)
            assert_safe_buttons(dump())
            screenshot(f'trips-entry-{density}-{locale}')
            adb('shell','input','swipe',227,330,227,95,400)
            tree=dump()
            assert title in texts(tree), ('Swipe up did not open trips',locale,texts(tree))
            screenshot(f'trips-summary-{density}-{locale}')
            card=None
            chart_seen=False
            for _ in range(14):
                chart=next((n for n in tree.iter('node') if n.get('content-desc','').startswith(trend + '. ')),None)
                if chart is not None and not chart_seen:
                    description=chart.get('content-desc').replace(',','.')
                    assert '5.2 L/100 km' in description and '5.9 L/100 km' in description, description
                    for _ in range(3):
                        _,top,_,bottom = map(int,re.findall(r'\d+',chart.get('bounds')))
                        delta=int(227-(top+bottom)/2)
                        if abs(delta) < 10:
                            break
                        adb('shell','input','swipe',227,227,227,227+max(-150,min(150,delta)),400)
                        tree=dump()
                        chart=next(n for n in tree.iter('node') if n.get('content-desc','').startswith(trend + '. '))
                    for label in (trend,chart_hint,chart_missing):
                        node=next(n for n in tree.iter('node') if n.get('text') == label)
                        _,top,_,bottom=map(int,re.findall(r'\d+',node.get('bounds')))
                        assert 30 <= top and bottom <= 424, ('Chart label clipped',locale,label,node.get('bounds'))
                    chart_seen=True
                    screenshot(f'trips-chart-{density}-{locale}')
                card=next((n for n in tree.iter('node') if n.get('clickable')=='true' and any(
                    c.get('text','').replace(',','.') == '5.2 L/100 km' for c in n.iter('node'))),None)
                if card is not None:
                    _,top,_,bottom = map(int,re.findall(r'\d+',card.get('bounds')))
                    if top >= 40 and bottom <= 414:
                        break
                    card = None  # Lazy lists can expose semantics for clipped, untappable cards.
                adb('shell','input','swipe',227,320,227,220,400)
                tree=dump()
            assert chart_seen, ('Consumption chart missing',locale)
            assert card is not None, 'Recent trip card missing'
            screenshot(f'trips-list-{density}-{locale}')
            tap(card)
            tree=dump()
            assert detail in texts(tree), ('Trip detail missing',locale,texts(tree))
            screenshot(f'trips-detail-{density}-{locale}')
            for _ in range(8):
                if ev_label in texts(tree) and '21.0%' in [t.replace(',','.') for t in texts(tree) if t]:
                    value=next(n for n in tree.iter('node') if n.get('text','').replace(',','.') == '21.0%')
                    _,top,_,bottom=map(int,re.findall(r'\d+',value.get('bounds')))
                    if bottom > 320:
                        adb('shell','input','swipe',227,320,227,160,400)
                        tree=dump()
                        assert '21.0%' in [t.replace(',','.') for t in texts(tree) if t]
                    screenshot(f'trips-ev-{density}-{locale}')
                    break
                adb('shell','input','swipe',227,320,227,180,400)
                tree=dump()
            else:
                raise AssertionError(('Electric distance missing',locale,texts(tree)))
            adb('shell','input','keyevent','KEYCODE_BACK')
            assert '43%' not in texts(dump()), 'Closing detail must stay in history'
            adb('shell','input','keyevent','KEYCODE_BACK')
            assert '43%' in texts(dump()), 'Back from history must return to vehicle'
            time.sleep(2)  # Vehicle entrance animation must finish before the next gesture.
            adb('shell','input','swipe',380,150,70,150,600)
            assert '43%' not in texts(dump()), 'Horizontal controls navigation must still work'
            adb('shell','input','swipe',90,150,390,150,600)
            assert '43%' in texts(dump()), 'Swipe right must return to status'
            adb('shell','input','swipe',5,227,420,227,600)
            assert 'Corolla Touring Sports - MY24' in texts(dump()), 'Left edge must return to garage'
            print(f'OK trips + chart + EV + back + controls + edge back: {locale}, {density} dpi',flush=True)


def check_about():
    version = re.search(r'versionName\s*=\s*"([^"]+)"', (ROOT / 'android/watch/build.gradle.kts').read_text()).group(1)
    def reveal(label):
        for _ in range(10):
            tree = dump()
            try:
                node = button(tree, label)
                left, top, right, bottom = map(int, re.findall(r'\d+', node.get('bounds')))
                if 40 <= top < bottom <= 414 and right > left:
                    for child in node.iter('node'):
                        if child.get('text') == label:
                            x1,y1,x2,y2 = map(int,re.findall(r'\d+',child.get('bounds')))
                            assert left <= x1 < x2 <= right and top <= y1 < y2 <= bottom, ('Clipped button text',label)
                            assert abs((x1+x2)-(left+right)) <= 4, ('Off-center button text',label)
                    return node
            except AssertionError as e:
                if not str(e).startswith('Button missing:'):
                    raise
            adb('shell','input','swipe',227,340,227,220,400)
        raise AssertionError(('Button not fully visible',label,texts(dump())))

    for density in (320,378):
        adb('shell','wm','density',density)
        for locale in ('en','es','de','fr','it','pt'):
            folder = 'values' if locale == 'en' else 'values-' + locale
            resources = ET.parse(ROOT / 'android/watch/src/main/res' / folder / 'strings.xml').getroot()
            labels = {n.get('name'):n.text for n in resources.findall('string')}
            launch('garage',locale)
            tap(button(dump(),labels['about_title']))
            tree = dump()
            assert any(version in (t or '') for t in texts(tree)), ('Missing installed version',locale)
            screenshot(f'about-version-{density}-{locale}')
            check = reveal(labels['about_check_updates'])
            screenshot(f'about-update-{density}-{locale}')
            if locale == 'es' and density == 378:
                before = ET.fromstring(adb('shell','run-as',PKG,'cat','shared_prefs/cache.xml'))
                checked_at = next((n.get('value') for n in before if n.get('name') == 'updateCheckedAt'), '0')
                tap(check)
                for _ in range(12):
                    tree = dump()
                    if labels['about_up_to_date'] in texts(tree):
                        break
                    time.sleep(1)
                else:
                    raise AssertionError(('Manual update check failed',texts(tree)))
                after = ET.fromstring(adb('shell','run-as',PKG,'cat','shared_prefs/cache.xml'))
                assert int(next(n.get('value') for n in after if n.get('name') == 'updateCheckedAt')) > int(checked_at), 'Manual check must bypass daily cache'
                screenshot('about-update-result-378-es')
            tap(reveal(labels['about_clear']))
            tree = dump()
            assert labels['about_clear_confirm'] in texts(tree), ('Missing wipe confirmation',locale)
            screenshot(f'about-confirm-{density}-{locale}')
            adb('shell','input','keyevent','KEYCODE_BACK')
            assert labels['about_clear_confirm'] not in texts(dump()), 'Cancel must close only the confirmation'
            adb('shell','input','keyevent','KEYCODE_BACK')
            assert 'Corolla Touring Sports - MY24' in texts(dump()), 'Cancel must keep the session and garage'
            print(f'OK about + version + centered buttons + clear/cancel: {locale}, {density} dpi',flush=True)

    # Emulator-only persisted dummy tokens: validate the actual wipe, not only its dialog.
    launch('about','es','--es','tokens', '\'{"accessToken":"demo","refreshToken":"demo","expiresAtMs":9223372036854775807,"uuid":"demo","brand":"T"}\'')
    before = ET.fromstring(adb('shell','run-as',PKG,'cat','shared_prefs/vault.xml'))
    assert any(n.get('name') == 'tokens' for n in before)
    tap(reveal('Limpiar credenciales'))
    tap(reveal('Confirmar'))
    assert 'Corolla Touring Sports - MY24' not in texts(dump())
    after = ET.fromstring(adb('shell','run-as',PKG,'cat','shared_prefs/vault.xml'))
    assert not any(n.get('name') in ('tokens','credentials') for n in after), 'Stored credentials must be removed'
    cache = ET.fromstring(adb('shell','run-as',PKG,'cat','shared_prefs/cache.xml'))
    assert not any(n.get('name') in ('vehicles','selected') or n.get('name','').startswith('raw:') for n in cache), 'Vehicle cache must be removed'
    adb('shell','am','force-stop',PKG)
    adb('shell','am','start','-n',PKG+'/.MainActivity')
    assert 'Corolla Touring Sports - MY24' not in texts(dump()), 'Wipe must survive app restart'
    tap(reveal('Información'))
    assert any(version in (t or '') for t in texts(dump())), 'About must be available without credentials'
    print('OK manual check bypass + confirmed credential wipe + restart + unlinked about',flush=True)


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
        if '--climate-options' in sys.argv:
            check_climate_options()
            sys.exit(0)
        if '--about' in sys.argv:
            check_about()
            sys.exit(0)
        if '--trips' in sys.argv:
            check_trips()
            sys.exit(0)
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
