# Инструкция по сбору аннотаций

## Зависимости

* Код ядра.
* CPAchecker: `https://github.com/mutilin/cpachecker`, ветка `muauto-match-deref`. Комадна сборки: `ant`.
* Kartographer: нужно пропатчить `kartographer/kartographer.py`, чтобы присваивание `cif_args` выглядело так:

    ```python
    cif_args = [cif,
                "--debug", "ALL",
                "--in", cif_in,
                "--aspect", aspect,
                "--back-end", "src",
                "--out", cif_out,
                "--keep"]
    ```

## Процедура запуска

1. В Makefile ядра необходимо закомментировать следующие строчки:

    ```make
    ifeq ($(shell $(CONFIG_SHELL) $(srctree)/scripts/gcc-goto.sh $(CC)), y)
        KBUILD_CFLAGS += -DCC_HAVE_ASM_GOTO
        KBUILD_AFLAGS += -DCC_HAVE_ASM_GOTO
    endif
    ```

    CPAchecker не понимает `asm goto`, а CIF не избавляется от него.

2. Запустить Kartographer на ядре, см. его `readme.txt`. В результате в его `workdir` должны получиться файл `km.json` и папка `cif`.
3. Запустить `plan.py`: `python3 scripts/null-deref/plan.py PATH_TO_KM.JSON plan.json`

    Аргументы: путь к `km.json`, путь к генерируему плану.

4. Запустить `run.py`: `python3 scripts/null-deref/run.py . PATH_TO_CIF plan.json annotations`

    Аргументы: путь к папке CPAchecker, путь к плану, путь к папке `cif`, путь к папке, в которую будут складываться аннотации.

    Также есть несколько опций, см. `--help`.

    По мере работы аннотации и логи складываются в `annotations`. Каждому анализируемому файлу соответствует папка,
    в ней `deref_annotation.txt` содержит аннотации в странном формате, а `log.txt` содержит лог запуска CPAchecker.

5. Запустить `collect.py`: `python3 scripts/null-deref.py/collect.py PATH_TO_KM.JSON annotations annotations.json`

    Аргументы: путь к `km.json`, папка с аннотациями, путь к генерируемому файлу с собранными аннотациями.

6. (опционально) Запустить `stats.py`: `python3 scripts/null-deref.py/stats.py PATH_TO_PLAN.JSON PATH_TO_ANNOTATIONS.JSON`

    Аргументы: путь к плану, путь к файлу с собранными аннотациями.

    Эта команда печатает общую статистику об аннотациях.

## Формат файла аннотаций

Полученный на 5-ом шаге `annotations.json` - JSON словарь, по цепочке ключей `[function_name][source_file_name]` получается словарь аннотаций одной функции, в нём ключи:

* "object file": в рамках какого объектного файла функция была проанализирована.
* "params": массив аннотаций отдельных параметров. Для каждого параметра - словарь с ключами:

    * "name": имя параметра.
    * "is pointer": true если параметр - указатель, иначе false.
    * "may deref", "must deref": флаги соответствующих аннотаций. Отсутствуют, если параметр не указатель.
