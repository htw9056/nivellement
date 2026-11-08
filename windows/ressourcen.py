#!/usr/bin/env python3
"""
Erzeugt eine Windows-Ressourcendatei (COFF .syso) mit Programmsymbol und Versionsinformation
für den Go-Starter. Keine externen Werkzeuge nötig.

Aufruf: python3 ressourcen.py <symbol.ico> <ausgabe.syso> <Version a.b.c.d> <Beschreibung> <Dateiname>
"""
import struct
import sys

RT_ICON, RT_GROUP_ICON, RT_VERSION = 3, 14, 16
SPRACHE = 0x0407  # Deutsch (Deutschland)
CODEPAGE = 0x04B0  # Unicode


def ico_lesen(pfad):
    d = open(pfad, "rb").read()
    _, typ, anzahl = struct.unpack_from("<HHH", d, 0)
    if typ != 1:
        raise SystemExit("keine ICO-Datei: " + pfad)
    bilder = []
    for i in range(anzahl):
        w, h, farben, res, ebenen, bits, groesse, offset = struct.unpack_from("<BBBBHHII", d, 6 + 16 * i)
        bilder.append(((w, h, farben, res, ebenen, bits), d[offset:offset + groesse]))
    return bilder


def gruppe(bilder):
    b = struct.pack("<HHH", 0, 1, len(bilder))
    for nr, (kopf, daten) in enumerate(bilder, 1):
        w, h, farben, res, ebenen, bits = kopf
        b += struct.pack("<BBBBHHIH", w, h, farben, res, ebenen, bits, len(daten), nr)
    return b


def ausrichten(b):
    return b + b"\0" * ((4 - len(b) % 4) % 4)


def block(schluessel, wert=b"", kinder=b"", typ=0, wert_laenge=None):
    """VS_VERSIONINFO-Block: wLength, wValueLength, wType, szKey, Padding, Value, Padding, Children."""
    k = (schluessel + "\0").encode("utf-16le")
    kopf_len = 6 + len(k)
    pad1 = (4 - kopf_len % 4) % 4
    rumpf = b"\0" * pad1 + wert
    pad2 = (4 - (kopf_len + len(rumpf)) % 4) % 4 if kinder else 0
    rumpf += b"\0" * pad2 + kinder
    if wert_laenge is None:
        wert_laenge = len(wert)
    return struct.pack("<HHH", kopf_len + len(rumpf), wert_laenge, typ) + k + rumpf


def text(schluessel, wert):
    w = (wert + "\0").encode("utf-16le")
    return ausrichten(block(schluessel, w, typ=1, wert_laenge=len(w) // 2))


def version(v, beschreibung, dateiname):
    teile = [int(x) for x in v.split(".")] + [0, 0, 0, 0]
    a, b, c, d = teile[:4]
    ms, ls = (a << 16) | b, (c << 16) | d
    fest = struct.pack("<13I", 0xFEEF04BD, 0x00010000, ms, ls, ms, ls, 0x3F, 0, 0x00040004, 1, 0, 0, 0)
    eintraege = {
        "CompanyName": "Dipl.-Ing. (FH) Stefan Klein",
        "FileDescription": beschreibung,
        "FileVersion": v,
        "InternalName": dateiname.rsplit(".", 1)[0],
        "LegalCopyright": "© Dipl.-Ing. (FH) Stefan Klein, MIT-Lizenz",
        "OriginalFilename": dateiname,
        "ProductName": "Nivellement",
        "ProductVersion": v,
    }
    tabelle = block("%04X%04X" % (SPRACHE, CODEPAGE), kinder=b"".join(text(k, w) for k, w in eintraege.items()),
                    typ=1)
    sfi = block("StringFileInfo", kinder=ausrichten(tabelle), typ=1)
    var = block("Translation", struct.pack("<HH", SPRACHE, CODEPAGE), typ=0)
    vfi = block("VarFileInfo", kinder=ausrichten(var), typ=1)
    return block("VS_VERSION_INFO", fest, ausrichten(sfi) + ausrichten(vfi), typ=0)


def syso(ressourcen, ausgabe):
    """ressourcen: {typ: {id: daten}} -> COFF-Objekt mit Abschnitt .rsrc und ADDR32NB-Relokationen."""
    typen = sorted(ressourcen)
    # Größen der Verzeichnisebenen
    def verz(n):
        return 16 + 8 * n

    groesse = verz(len(typen))
    for t in typen:
        groesse += verz(len(ressourcen[t]))
        for _ in ressourcen[t]:
            groesse += verz(1)
    daten_eintraege_start = groesse
    anzahl = sum(len(ressourcen[t]) for t in typen)
    groesse += 16 * anzahl
    daten_start = groesse

    buf = bytearray()
    relok = []
    # Ebene 1 (Typen)
    ebene2 = verz(len(typen))
    buf += struct.pack("<IIHHHH", 0, 0, 0, 0, 0, len(typen))
    offs = ebene2
    pos2 = []
    for t in typen:
        buf += struct.pack("<II", t, 0x80000000 | offs)
        pos2.append(offs)
        offs += verz(len(ressourcen[t]))
    ebene3 = offs
    # Ebene 2 (IDs)
    offs3 = ebene3
    blaetter = []
    for t in typen:
        ids = sorted(ressourcen[t])
        buf += struct.pack("<IIHHHH", 0, 0, 0, 0, 0, len(ids))
        for i in ids:
            buf += struct.pack("<II", i, 0x80000000 | offs3)
            blaetter.append((offs3, ressourcen[t][i]))
            offs3 += verz(1)
    # Ebene 3 (Sprache)
    eintrag = daten_eintraege_start
    roh = bytearray()
    for _, daten in blaetter:
        buf += struct.pack("<IIHHHH", 0, 0, 0, 0, 0, 1)
        buf += struct.pack("<II", SPRACHE, eintrag)
        eintrag += 16
    # Datenbeschreibungen
    for _, daten in blaetter:
        rva = daten_start + len(roh)
        relok.append(len(buf))
        buf += struct.pack("<IIII", rva, len(daten), 0, 0)
        roh += ausrichten(bytes(daten))
    assert len(buf) == daten_start
    buf += roh

    kopf_len = 20 + 40
    roh_pos = kopf_len
    relok_pos = roh_pos + len(buf)
    sym_pos = relok_pos + 10 * len(relok)
    coff = struct.pack("<HHIIIHH", 0x8664, 1, 0, sym_pos, 1, 0, 0)
    abschnitt = struct.pack("<8sIIIIIIHHI", b".rsrc", 0, 0, len(buf), roh_pos, relok_pos, 0, len(relok), 0,
                            0x40000040)
    rel = b"".join(struct.pack("<IIH", r, 0, 3) for r in relok)  # IMAGE_REL_AMD64_ADDR32NB
    sym = struct.pack("<8sIhHBB", b".rsrc", 0, 1, 0, 3, 0)
    tab = struct.pack("<I", 4)
    open(ausgabe, "wb").write(coff + abschnitt + bytes(buf) + rel + sym + tab)


def main():
    ico, aus, v, beschreibung, dateiname = sys.argv[1:6]
    bilder = ico_lesen(ico)
    res = {
        RT_ICON: {i: d for i, (_, d) in enumerate(bilder, 1)},
        RT_GROUP_ICON: {1: gruppe(bilder)},
        RT_VERSION: {1: version(v, beschreibung, dateiname)},
    }
    syso(res, aus)


if __name__ == "__main__":
    main()
