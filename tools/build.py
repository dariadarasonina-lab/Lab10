"""Build, run and verify using only Python's stdlib and a JDK >= 17."""
import argparse
import json
import subprocess
import sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import generate
import reverse


def run(command, **kwargs):
    result = subprocess.run(command, cwd=ROOT, text=True, encoding='utf-8', capture_output=True, **kwargs)
    if result.returncode:
        print(result.stdout, result.stderr)
        raise SystemExit(result.returncode)
    return result.stdout


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--verify', action='store_true')
    args = parser.parse_args()
    (ROOT / 'evidence').mkdir(exist_ok=True)
    generate.main()
    classes = ROOT / 'build/classes'
    classes.mkdir(parents=True, exist_ok=True)
    sources = sorted((ROOT / 'src/main/java').rglob('*.java'))
    build_command = ['javac', '-encoding', 'UTF-8', '--release', '17', '-d', str(classes), *map(str, sources)]
    run(build_command)
    version = run(['javac', '-version']).strip()
    log = f'{version}\n$ javac -encoding UTF-8 --release 17 -d build/classes <{len(sources)} Java files>\nКомпиляция: успешно (exit 0)\n'
    api = reverse.main()
    # Enforce equality between model declarations and javap API (excluding synthetic lambda methods).
    model = json.loads((ROOT / 'model/university.json').read_text())
    simplify = lambda s: __import__('re').sub(r'(?:[a-z_][\w]*\.)+([A-Z]\w*)', r'\1', s)
    for cls in model['classes']:
        expected = {f.split(' = ')[0] for f in cls['fields']}
        for method in cls['methods']:
            signature = method['signature']
            prefix, params = signature.split('(', 1)
            types = [p.rsplit(' ', 1)[0] for p in params[:-1].split(', ') if p]
            expected.add(prefix + '(' + ', '.join(types) + ')')
        actual = {simplify(s) for s in api['ru.lab10.domain.' + cls['name']]['members']}
        assert expected == actual, f'API mismatch: {cls["name"]}: {expected ^ actual}'
    log += 'Согласованность модели и байткода: 9 классов, все поля и сигнатуры совпадают\n'
    if args.verify:
        tests = ROOT / 'build/tests'
        tests.mkdir(parents=True, exist_ok=True)
        run(['javac', '-encoding', 'UTF-8', '--release', '17', '-cp', str(classes), '-d', str(tests),
             str(ROOT / 'src/test/java/ru/lab10/DomainTest.java')])
        import os
        log += run(['java', '-cp', str(classes) + os.pathsep + str(tests), 'ru.lab10.DomainTest'])
        sample = (ROOT / 'examples/interactive-input.txt').read_text()
        interactive = run(['java', '-cp', str(classes), 'ru.lab10.cli.App'], input=sample)
        assert 'Курс назначен' in interactive and 'Сотрудников научно-педагогического состава: 1' in interactive
        assert 'Сотрудник: L-100' in interactive and 'Ошибка:' not in interactive
        (ROOT / 'evidence/interactive-output.txt').write_text(interactive)
        bad_input = '4\nТест\nwrong-date\n0\n'
        bad = run(['java', '-cp', str(classes), 'ru.lab10.cli.App'], input=bad_input)
        assert 'Ошибка:' in bad and 'could not be parsed' in bad
        log += 'PASS: интерактивный ввод UC-01…UC-06 и обработка неверной даты\n'
    log += '\n$ java -cp build/classes ru.lab10.cli.App --demo\n'
    demo = run(['java', '-cp', str(classes), 'ru.lab10.cli.App', '--demo'])
    log += demo
    (ROOT / 'evidence/build-and-demo.txt').write_text(log)
    print(log)

if __name__ == '__main__':
    main()
