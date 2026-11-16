#!/usr/bin/env python3
"""Run the opt-in native/manual probe using the last focused test classpath."""
import pathlib
import subprocess
import sys
import xml.etree.ElementTree as ET

module = pathlib.Path(__file__).resolve().parent.parent
report = module / 'target/surefire-reports/TEST-de.neitzel.fx.view.FilterEditorsTest.xml'
if not report.exists():
    sys.exit('First run the documented focused Maven tests including FilterEditorsTest.')
properties = {p.get('name'): p.get('value') for p in ET.parse(report).getroot().find('properties')}
classpath = properties.get('surefire.test.class.path', properties['java.class.path'])
raise SystemExit(subprocess.call(['java', '--enable-native-access=ALL-UNNAMED', '-cp', classpath,
    'de.neitzel.fx.view.FilterPopupNativeProbe', *sys.argv[1:]], cwd=module.parent))
