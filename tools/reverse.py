"""Reverse engineer the compiled Java API, never reading the forward JSON model.
Synthetic lambda/bridge methods are implementation artifacts and are excluded.
"""
import re
import subprocess
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]

def main():
    classpath = ROOT / 'build/classes'
    files = sorted(classpath.rglob('*.class'))
    declarations, relations, apis, raw = [], set(), {}, []
    names = {p.stem for p in files if '$' not in p.stem}
    for path in files:
        if '$' in path.stem:
            continue
        full = '.'.join(path.relative_to(classpath).with_suffix('').parts)
        # -p gives all access levels. -v identifies synthetic members, including lambdas.
        output = subprocess.check_output(['javap', '-private', '-classpath', str(classpath), full], text=True)
        raw.append(output)
        header = next(line for line in output.splitlines() if line.endswith('{'))
        name = path.stem
        members = []
        signatures = []
        for line in output.splitlines():
            line = line.strip()
            if not line.endswith(';') or 'lambda$' in line or line == 'static {};':
                continue
            line = line.removesuffix(';')
            signatures.append(line)
            visibility = '+' if line.startswith('public ') else '-' if line.startswith('private ') else '#' if line.startswith('protected ') else '~'
            member = re.sub(r'^(public|private|protected) ', '', line)
            member = member.replace(full, name)
            member = re.sub(r'(?:[a-z_][\w]*\.)+([A-Z]\w*)', r'\1', member)
            members.append('  ' + visibility + member)
            if '(' not in line:
                field_name = line.split()[-1]
                # Each association is derived from an actual declared field's generic type.
                for target in sorted(names - {name}):
                    if re.search(r'\b' + re.escape(target) + r'\b', line):
                        relations.add(f'{name} --> {target} : {field_name}')
        parent = re.search(r' extends ([\w.]+)', header)
        if parent and parent.group(1).split('.')[-1] in names:
            relations.add(parent.group(1).split('.')[-1] + ' <|-- ' + name)
        declarations += [('abstract class ' if ' abstract ' in header else 'class ') + name + ' {', *members, '}']
        apis[full] = dict(header=header, members=signatures)
    diagram = ['@startuml', '!pragma layout smetana', 'skinparam classAttributeIconSize 0', 'skinparam shadowing false',
               'skinparam defaultFontName SansSerif', 'title Обратный инжиниринг: javap → классы, поля и методы', *declarations, *sorted(relations), '@enduml', '']
    (ROOT / 'diagrams/03-class-reverse.puml').write_text('\n'.join(diagram))
    import json
    (ROOT / 'evidence/reverse-api.json').write_text(json.dumps(apis, ensure_ascii=False, indent=2)+'\n')
    (ROOT / 'evidence/javap.txt').write_text('\n'.join(raw))
    print(f'Reverse engineered {len(apis)} classes from compiled bytecode.')
    return apis

if __name__ == '__main__':
    main()
