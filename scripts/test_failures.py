import os
"""Print failing unit tests (name | message) from the last Gradle run."""
import glob
import re

for x in glob.glob(os.environ.get('PV_BUILD_BASH','app/build').replace('/c/','C:/',1)+'/test-results/testDebugUnitTest/*.xml'):
    t = open(x, encoding='utf-8').read()
    for m in re.finditer(r'<testcase name="([^"]+)"[^>]*>\s*<failure message="([^"]{0,220})', t):
        print(m.group(1), '|', m.group(2))
