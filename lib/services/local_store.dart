import 'dart:convert';

import 'package:path/path.dart' as p;
import 'package:sqflite/sqflite.dart';

class LocalStore {
  LocalStore._(this._db);

  final Database _db;

  static Future<LocalStore> open({DatabaseFactory? factory, String? path}) async {
    final selectedFactory = factory ?? databaseFactory;
    final selectedPath = path ?? p.join(await getDatabasesPath(), 'linkmesh_v2.db');
    final db = await selectedFactory.openDatabase(
      selectedPath,
      options: OpenDatabaseOptions(
        version: 1,
        onCreate: (database, _) => database.execute(
          'CREATE TABLE objects(collection TEXT NOT NULL, object_id TEXT NOT NULL, json TEXT NOT NULL, sort_order INTEGER NOT NULL, PRIMARY KEY(collection, object_id))',
        ),
      ),
    );
    return LocalStore._(db);
  }

  Future<List<Map<String, dynamic>>> readCollection(String collection) async {
    final rows = await _db.query('objects', columns: ['json'], where: 'collection = ?', whereArgs: [collection], orderBy: 'sort_order ASC');
    return rows.map((row) => Map<String, dynamic>.from(jsonDecode(row['json']! as String) as Map)).toList();
  }

  Future<void> replaceCollection(String collection, Iterable<Map<String, dynamic>> objects) async {
    await _db.transaction((transaction) async {
      await transaction.delete('objects', where: 'collection = ?', whereArgs: [collection]);
      var order = 0;
      for (final object in objects) {
        await transaction.insert('objects', {
          'collection': collection,
          'object_id': object['id']?.toString() ?? '$collection-$order',
          'json': jsonEncode(object),
          'sort_order': order++,
        });
      }
    });
  }

  Future<void> close() => _db.close();
}
