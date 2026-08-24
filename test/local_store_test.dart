import 'package:flutter_test/flutter_test.dart';
import 'package:linkmesh_offline_chat/services/local_store.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

void main() {
  setUpAll(sqfliteFfiInit);

  test('SQLite collection preserves order and replaces old records', () async {
    final store = await LocalStore.open(factory: databaseFactoryFfi, path: inMemoryDatabasePath);
    await store.replaceCollection('messages', [
      {'id': '1', 'text': 'first'},
      {'id': '2', 'text': 'second'},
    ]);
    expect(await store.readCollection('messages'), [
      {'id': '1', 'text': 'first'},
      {'id': '2', 'text': 'second'},
    ]);

    await store.replaceCollection('messages', [
      {'id': '3', 'text': 'replacement'},
    ]);
    expect(await store.readCollection('messages'), [
      {'id': '3', 'text': 'replacement'},
    ]);
    await store.close();
  });

  test('collections remain isolated', () async {
    final store = await LocalStore.open(factory: databaseFactoryFfi, path: inMemoryDatabasePath);
    await store.replaceCollection('peers', [{'id': 'p1'}]);
    await store.replaceCollection('groups', [{'id': 'g1'}]);
    expect((await store.readCollection('peers')).single['id'], 'p1');
    expect((await store.readCollection('groups')).single['id'], 'g1');
    await store.close();
  });
}
