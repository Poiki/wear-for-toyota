# /// script
# requires-python = ">=3.10"
# dependencies = ["pytoyoda==5.2.9"]
# ///
"""Fase 2, READ ONLY: vuelca lo que Toyota devuelve para la cuenta. Solo GETs, ningún comando ni wake.

Uso:   uv run probe/probe.py          (pide email y contraseña por teclado; no se guardan en ningún sitio)
       uv run probe/probe.py --check  (solo comprueba que pytoyoda se instala e importa)
Salida: probe/out/<fecha>/raw.json con todas las respuestas (local, ignorado por git) y un resumen
        redactado por pantalla (VIN enmascarado, sin coordenadas).
"""

import asyncio
import datetime as dt
import getpass
import json
import pathlib
import sys

OUT_DIR = pathlib.Path(__file__).resolve().parent / "out"


def dump(obj):
    return obj.model_dump(mode="json", by_alias=True) if hasattr(obj, "model_dump") else obj


def true_keys(d):
    return sorted(k for k, v in (d or {}).items() if v is True)


def mask(vin):
    return f"…{vin[-4:]}" if vin else "?"


def show(label, value):
    if value is not None and value != [] and value != {}:
        print(f"  {label}: {value}")


def summarize(v, info):
    print(f"\n=== {v.alias or '(sin alias)'} · {info.get('modelName')} {info.get('modelYear')} · VIN {mask(v.vin)} · tipo {v.type} · fuelType {info.get('fuelType')}")
    show("remoteDisplay (7 = activado)", info.get("remoteDisplay"))
    show("remoteSubscriptionStatus", info.get("remoteSubscriptionStatus"))
    show("subscriptions", [f"{s.get('productCode')}:{s.get('status')}" for s in info.get("subscriptions") or []])
    show("extendedCapabilities=true", true_keys(info.get("extendedCapabilities")))
    show("remoteServiceCapabilities=true", true_keys(info.get("remoteServiceCapabilities")))
    show("features=true", true_keys(info.get("features")))
    d = v.dashboard
    if d:
        show("odómetro km", d.odometer)
        show("combustible %", d.fuel_level)
        show("autonomía combustible km", d.fuel_range)
        show("batería HV %", d.battery_level)
        show("autonomía EV km", d.battery_range)
        show("autonomía total km", d.range)
        show("carga", d.charging_status)
        show("testigos", d.warning_lights)
    ls = v.lock_status
    if ls:
        show("estado actualizado", ls.last_updated)
        doors = ls.doors
        if doors:
            for name in ("driver_seat", "passenger_seat", "driver_rear_seat", "passenger_rear_seat", "trunk"):
                door = getattr(doors, name)
                if door:
                    show(f"puerta {name}", f"cerrada={door.closed} bloqueada={door.locked}")
        if ls.hood:
            show("capó cerrado", ls.hood.closed)
        win = ls.windows
        if win:
            show("ventanas cerradas", [getattr(win, n).closed for n in ("driver_seat", "passenger_seat", "driver_rear_seat", "passenger_rear_seat") if getattr(win, n)])
    loc = v.location
    if loc:
        show("ubicación disponible", loc.latitude is not None)
        show("ubicación actualizada", loc.timestamp)
    es = v.electric_status
    if es:
        show("EV batería %", es.battery_level)
        show("EV carga", es.charging_status)
        show("EV autonomía km", es.ev_range)
        show("EV actualizado", es.last_update_timestamp)
    cs = v.climate_status
    if cs:
        show("clima estado", cs.status)
        show("clima objetivo", cs.target_temperature)
        show("clima actualizado", cs.updated_at)
    if v.climate_settings:
        show("clima ajustes guardados", f"temp={v.climate_settings.temperature} duración={v.climate_settings.duration}")
    notes = v.notifications or []
    show("notificaciones", f"{len(notes)}; últimas: " + " | ".join(f"{n.date:%Y-%m-%d} {n.category}: {n.message}" for n in notes[:3]))
    show("endpoints con error", {k: f"{type(e).__name__}: {e}" for k, e in v._endpoint_errors.items()})


async def main():
    from pytoyoda import MyT

    user = input("Email Toyota: ").strip()
    password = getpass.getpass("Contraseña (no se muestra ni se guarda): ")
    brand = (input("Marca [T=Toyota, L=Lexus] (T): ").strip() or "T").upper()
    client = MyT(user, password, use_metric=True, brand=brand)
    del password
    await client.login()
    print("Login OK. Leyendo vehículos (solo GET, en serie)…")
    vehicles = [v for v in await client.get_vehicles() if v]
    out = OUT_DIR / dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    out.mkdir(parents=True)
    raw = []
    for v in vehicles:
        try:
            await v.update()
        except Exception as ex:  # noqa: BLE001 — volcamos lo que haya llegado
            print(f"  update() falló: {type(ex).__name__}: {ex}")
        info = dump(v._vehicle_info)
        raw.append({
            "vehicleInfo": info,
            "endpoints": {k: dump(d) for k, d in v._endpoint_data.items()},
            "endpointErrors": {k: f"{type(e).__name__}: {e}" for k, e in v._endpoint_errors.items()},
        })
        summarize(v, info)
    (out / "raw.json").write_text(json.dumps(raw, indent=2, ensure_ascii=False, default=str), encoding="utf-8")
    await client.aclose()
    print(f"\n{len(vehicles)} vehículo(s). Datos completos en {out / 'raw.json'} (contiene VIN y coordenadas: no compartir).")


if __name__ == "__main__":
    if "--check" in sys.argv:
        import pytoyoda

        print("pytoyoda", getattr(pytoyoda, "__version__", "?"), "OK")
        sys.exit(0)
    asyncio.run(main())
