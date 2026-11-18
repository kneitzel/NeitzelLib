#!/usr/bin/env python3
"""Launch the real DataGrid native qualification surface after DataGridGroupingTest.

On macOS, --app creates an addressable app bundle for native accessibility tools.
All generated artifacts stay under fx/target. No application data or persistence.
"""
import pathlib
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

module = pathlib.Path(__file__).resolve().parent.parent
report = module / 'target/surefire-reports/TEST-de.neitzel.fx.view.DataGridGroupingTest.xml'
if not report.exists():
    sys.exit('First run ./mvnw -pl fx -am -Dtest=DataGridGroupingTest -Dsurefire.failIfNoSpecifiedTests=false test')
properties = {p.get('name'): p.get('value') for p in ET.parse(report).getroot().find('properties')}
classpath = properties.get('surefire.test.class.path', properties['java.class.path'])
app_name = 'DataGridProbe'
main = 'de.neitzel.fx.view.DataGridNativeProbe'
if '--app' in sys.argv:
    work = module / 'target/native-probe'
    inputs = work / 'input'
    inputs.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(inputs / 'probe.jar', 'w') as archive:
        seen = set()
        for entry in classpath.split(':'):
            path = pathlib.Path(entry)
            if path.is_dir():
                for file in path.rglob('*'):
                    if file.is_file() and str(file.relative_to(path)) not in seen:
                        relative = str(file.relative_to(path))
                        seen.add(relative)
                        archive.write(file, relative)
            elif path.suffix == '.jar':
                target = inputs / path.name
                if target.exists() and target.read_bytes() != path.read_bytes():
                    sys.exit('Conflicting dependency filename: ' + path.name)
                shutil.copy2(path, target)
    app = work / (app_name + '.app')
    if app.exists():
        shutil.rmtree(app)
    subprocess.run(['jpackage', '--type', 'app-image', '--name', app_name,
        '--input', str(inputs), '--dest', str(work), '--main-jar', 'probe.jar',
        '--main-class', main, '--mac-package-identifier', 'de.neitzel.datagrid.probe',
        '--java-options', '--enable-native-access=ALL-UNNAMED',
        '--java-options', '-Dneitzellib.nativeGridOutput=' + str(module / 'target/native-grid')], check=True)
    print(app, flush=True)
    raise SystemExit(subprocess.call([str(app / ('Contents/MacOS/' + app_name))]))
raise SystemExit(subprocess.call(['java', '--enable-native-access=ALL-UNNAMED', '-cp', classpath,
    main], cwd=module.parent))
