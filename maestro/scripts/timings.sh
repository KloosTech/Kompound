#!/usr/bin/env bash
# Where did a run spend its time? Reads the commands.json files of a run (default: build/maestro/artifacts) and prints, per command type,
# how often it ran, the average and the slowest duration, then the 10 slowest single steps with their flow.
#   timings.sh [artifacts-dir]
DIR="${1:-build/maestro/artifacts}"
python3 - "$DIR" <<'PYEOF'
import json, glob, sys, collections
files = glob.glob(sys.argv[1] + '/**/commands.json', recursive=True)
if not files: sys.exit("no commands.json under " + sys.argv[1])
stats = collections.defaultdict(list); steps = []
for f in files:
    flow = f.split('/')[-2]
    for c in json.load(open(f)):
        cmd = c.get('command', {}); k = next(iter(cmd), '?')
        if k in ('defineVariablesCommand', 'applyConfigurationCommand', 'runFlowCommand'): continue   # bookkeeping and containers (their time is in their children)
        d = c.get('metadata', {}).get('duration') or 0
        stats[k].append(d); steps.append((d, k, flow))
total = sum(sum(v) for v in stats.values())
print("%-28s %5s %8s %8s %9s" % ("command", "count", "avg ms", "max ms", "total s"))
for k, v in sorted(stats.items(), key=lambda kv: -sum(kv[1])):
    print("%-28s %5d %8d %8d %9.1f" % (k, len(v), sum(v) / len(v), max(v), sum(v) / 1000))
print("%-28s %5s %8s %8s %9.1f\n" % ("all steps", "", "", "", total / 1000))
for d, k, flow in sorted(steps, reverse=True)[:10]: print("%7d ms  %-22s %s" % (d, k, flow))
PYEOF
