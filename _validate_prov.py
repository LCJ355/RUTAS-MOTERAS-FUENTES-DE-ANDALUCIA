c = open(r'index.html', encoding='utf-8').read()
checks = [
    ('province-select', 'id="province-select"' in c),
    ('town-select', 'id="town-select"' in c),
    ('PROVS', 'var PROVS' in c),
    ('TP object', 'var TP' in c),
    ('updProv', 'function updProv' in c),
]
for name, ok in checks:
    print(f'  {name}: {"OK" if ok else "MISSING"}')

# Verify CSS and JS links
print(f'  markercluster CSS: {"MarkerCluster.css" in c}')
print(f'  markercluster JS: {"leaflet.markercluster.js" in c}')
print(f'  mcg: {"L.markerClusterGroup" in c}')
print(f'  Size: {len(c)} bytes')
