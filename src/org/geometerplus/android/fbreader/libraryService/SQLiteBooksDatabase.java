/*
 * Copyright (C) 2009-2013 Geometer Plus <contact@geometerplus.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.android.fbreader.libraryService;

import java.util.*;
import java.math.BigDecimal;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.database.SQLException;
import android.database.Cursor;

import org.geometerplus.zlibrary.core.config.ZLConfig;
import org.geometerplus.zlibrary.core.filesystem.ZLFile;
import org.geometerplus.zlibrary.core.options.ZLStringOption;
import org.geometerplus.zlibrary.core.options.ZLIntegerOption;
import org.geometerplus.zlibrary.core.util.RationalNumber;
import org.geometerplus.zlibrary.core.util.ZLColor;
import org.geometerplus.zlibrary.text.view.ZLTextPosition;
import org.geometerplus.zlibrary.text.view.ZLTextFixedPosition;

import org.geometerplus.fbreader.book.*;

import org.geometerplus.android.util.SQLiteUtil;

final class SQLiteBooksDatabase extends BooksDatabase {
	private static BooksDatabase ourInstance;

	static BooksDatabase Instance(Context context) {
		if (ourInstance == null) {
			ourInstance = new SQLiteBooksDatabase(context);
		}
		return ourInstance;
	}

	private final SQLiteDatabase myDatabase;

	private SQLiteBooksDatabase(Context context) {
		myDatabase = context.openOrCreateDatabase("books.db", Context.MODE_PRIVATE, null);
		migrate();
	}

	protected void executeAsTransaction(Runnable actions) {
		boolean transactionStarted = false;
		try {
			myDatabase.beginTransaction();
			transactionStarted = true;
		} catch (Throwable t) {
		}
		try {
			actions.run();
			if (transactionStarted) {
				myDatabase.setTransactionSuccessful();
			}
		} finally {
			if (transactionStarted) {
				myDatabase.endTransaction();
			}
		}
	}

	private void migrate() {
		final int version = myDatabase.getVersion();
		final int currentVersion = 26;
		if (version >= currentVersion) {
			return;
		}

		myDatabase.beginTransaction();

		switch (version) {
			case 0:
				createTables();
			case 1:
				updateTables1();
			case 2:
				updateTables2();
			case 3:
				updateTables3();
			case 4:
				updateTables4();
			case 5:
				updateTables5();
			case 6:
				updateTables6();
			case 7:
				updateTables7();
			case 8:
				updateTables8();
			case 9:
				updateTables9();
			case 10:
				updateTables10();
			case 11:
				updateTables11();
			case 12:
				updateTables12();
			case 13:
				updateTables13();
			case 14:
				updateTables14();
			case 15:
				updateTables15();
			case 16:
				updateTables16();
			case 17:
				updateTables17();
			case 18:
				updateTables18();
			case 19:
				updateTables19();
			case 20:
				updateTables20();
			case 21:
				updateTables21();
			case 22:
				updateTables22();
			case 23:
				updateTables23();
			case 24:
				updateTables24();
			case 25:
				updateTables25();
		}
		myDatabase.setTransactionSuccessful();
		myDatabase.setVersion(currentVersion);
		myDatabase.endTransaction();

		myDatabase.execSQL("VACUUM");
	}

	@Override
	protected Book loadBook(long bookId) {
		Book book = null;
		final Cursor cursor = myDatabase.rawQuery("SELECT file_id,title,encoding,language FROM Books WHERE book_id = " + bookId, null);
		if (cursor.moveToNext()) {
			book = createBook(
				bookId, cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3)
			);
		}
		cursor.close();
		book.setProgress(loadProgress(book.getId()));
		return book;
	}

	protected Book loadBookByFile(long fileId, ZLFile file) {
		if (fileId == -1) {
			return null;
		}
		Book book = null;
		final Cursor cursor = myDatabase.rawQuery("SELECT book_id,title,encoding,language FROM Books WHERE file_id = " + fileId, null);
		if (cursor.moveToNext()) {
			book = createBook(
				cursor.getLong(0), file, cursor.getString(1), cursor.getString(2), cursor.getString(3)
			);
		}
		cursor.close();
		return book;
	}

	private boolean myTagCacheIsInitialized;
	private final HashMap<Tag,Long> myIdByTag = new HashMap<Tag,Long>();
	private final HashMap<Long,Tag> myTagById = new HashMap<Long,Tag>();

	private void initTagCache() {
		if (myTagCacheIsInitialized) {
			return;
		}
		myTagCacheIsInitialized = true;

		Cursor cursor = myDatabase.rawQuery("SELECT tag_id,parent_id,name FROM Tags ORDER BY tag_id", null);
		while (cursor.moveToNext()) {
			long id = cursor.getLong(0);
			if (myTagById.get(id) == null) {
				final Tag tag = Tag.getTag(myTagById.get(cursor.getLong(1)), cursor.getString(2));
				myIdByTag.put(tag, id);
				myTagById.put(id, tag);
			}
		}
		cursor.close();
	}

	@Override
	protected Map<Long,Book> loadBooks(FileInfoSet infos, boolean existing) {
		Cursor cursor = myDatabase.rawQuery(
			"SELECT book_id,file_id,title,encoding,language FROM Books WHERE `exists` = " + (existing ? 1 : 0), null
		);
		final HashMap<Long,Book> booksById = new HashMap<Long,Book>();
		final HashMap<Long,Book> booksByFileId = new HashMap<Long,Book>();
		while (cursor.moveToNext()) {
			final long id = cursor.getLong(0);
			final long fileId = cursor.getLong(1);
			final Book book = createBook(
				id, infos.getFile(fileId), cursor.getString(2), cursor.getString(3), cursor.getString(4)
			);
			if (book != null) {
				booksById.put(id, book);
				booksByFileId.put(fileId, book);
			}
		}
		cursor.close();

		initTagCache();

		cursor = myDatabase.rawQuery(
			"SELECT author_id,name,sort_key FROM Authors", null
		);
		final HashMap<Long,Author> authorById = new HashMap<Long,Author>();
		while (cursor.moveToNext()) {
			authorById.put(cursor.getLong(0), new Author(cursor.getString(1), cursor.getString(2)));
		}
		cursor.close();

		cursor = myDatabase.rawQuery(
			"SELECT book_id,author_id FROM BookAuthor ORDER BY author_index", null
		);
		while (cursor.moveToNext()) {
			final Book book = booksById.get(cursor.getLong(0));
			if (book != null) {
				Author author = authorById.get(cursor.getLong(1));
				if (author != null) {
					addAuthor(book, author);
				}
			}
		}
		cursor.close();

		cursor = myDatabase.rawQuery("SELECT book_id,tag_id FROM BookTag", null);
		while (cursor.moveToNext()) {
			final Book book = booksById.get(cursor.getLong(0));
			if (book != null) {
				addTag(book, getTagById(cursor.getLong(1)));
			}
		}
		cursor.close();

		cursor = myDatabase.rawQuery(
			"SELECT series_id,name FROM Series", null
		);
		final HashMap<Long,String> seriesById = new HashMap<Long,String>();
		while (cursor.moveToNext()) {
			seriesById.put(cursor.getLong(0), cursor.getString(1));
		}
		cursor.close();

		cursor = myDatabase.rawQuery(
			"SELECT book_id,series_id,book_index FROM BookSeries", null
		);
		while (cursor.moveToNext()) {
			final Book book = booksById.get(cursor.getLong(0));
			if (book != null) {
				final String series = seriesById.get(cursor.getLong(1));
				if (series != null) {
					setSeriesInfo(book, series, cursor.getString(2));
				}
			}
		}
		cursor.close();

		cursor = myDatabase.rawQuery(
			"SELECT book_id,type,uid FROM BookUid", null
		);
		while (cursor.moveToNext()) {
			final Book book = booksById.get(cursor.getLong(0));
			if (book != null) {
				book.addUid(cursor.getString(1), cursor.getString(2));
			}
		}
		cursor.close();

		cursor = myDatabase.rawQuery(
			"SELECT BookLabel.book_id,Labels.name FROM Labels" +
			" INNER JOIN BookLabel ON BookLabel.label_id=Labels.label_id",
			null
		);
		while (cursor.moveToNext()) {
			final Book book = booksById.get(cursor.getLong(0));
			if (book != null) {
				book.addLabel(cursor.getString(1));
			}
		}
		cursor.close();

		cursor = myDatabase.rawQuery(
			"SELECT book_id FROM Bookmarks WHERE visible = 1 GROUP by book_id",
			null
		);
		while (cursor.moveToNext()) {
			final Book book = booksById.get(cursor.getLong(0));
			if (book != null) {
				book.HasBookmark = true;
			}
		}
		cursor.close();

		return booksByFileId;
	}

	@Override
	protected void setExistingFlag(Collection<Book> books, boolean flag) {
		if (books.isEmpty()) {
			return;
		}
		final StringBuilder bookSet = new StringBuilder("(");
		boolean first = true;
		for (Book b : books) {
			if (first) {
				first = false;
			} else {
				bookSet.append(",");
			}
			bookSet.append(b.getId());
		}
		bookSet.append(")");
		myDatabase.execSQL(
			"UPDATE Books SET `exists` = " + (flag ? 1 : 0) + " WHERE book_id IN " + bookSet
		);
	}

	private SQLiteStatement myUpdateBookInfoStatement;
	@Override
	protected void updateBookInfo(long bookId, long fileId, String encoding, String language, String title) {
		if (myUpdateBookInfoStatement == null) {
			myUpdateBookInfoStatement = myDatabase.compileStatement(
				"UPDATE OR IGNORE Books SET file_id = ?, encoding = ?, language = ?, title = ? WHERE book_id = ?"
			);
		}
		myUpdateBookInfoStatement.bindLong(1, fileId);
		SQLiteUtil.bindString(myUpdateBookInfoStatement, 2, encoding);
		SQLiteUtil.bindString(myUpdateBookInfoStatement, 3, language);
		myUpdateBookInfoStatement.bindString(4, title);
		myUpdateBookInfoStatement.bindLong(5, bookId);
		myUpdateBookInfoStatement.execute();
	}

	private SQLiteStatement myInsertBookInfoStatement;
	@Override
	protected long insertBookInfo(ZLFile file, String encoding, String language, String title) {
		if (myInsertBookInfoStatement == null) {
			myInsertBookInfoStatement = myDatabase.compileStatement(
				"INSERT OR IGNORE INTO Books (encoding,language,title,file_id) VALUES (?,?,?,?)"
			);
		}
		SQLiteUtil.bindString(myInsertBookInfoStatement, 1, encoding);
		SQLiteUtil.bindString(myInsertBookInfoStatement, 2, language);
		myInsertBookInfoStatement.bindString(3, title);
		final FileInfoSet infoSet = new FileInfoSet(this, file);
		myInsertBookInfoStatement.bindLong(4, infoSet.getId(file));
		return myInsertBookInfoStatement.executeInsert();
	}

	private SQLiteStatement myDeleteBookAuthorsStatement;
	protected void deleteAllBookAuthors(long bookId) {
		if (myDeleteBookAuthorsStatement == null) {
			myDeleteBookAuthorsStatement = myDatabase.compileStatement(
				"DELETE FROM BookAuthor WHERE book_id = ?"
			);
		}
		myDeleteBookAuthorsStatement.bindLong(1, bookId);
		myDeleteBookAuthorsStatement.execute();
	}

	private SQLiteStatement myGetAuthorIdStatement;
	private SQLiteStatement myInsertAuthorStatement;
	private SQLiteStatement myInsertBookAuthorStatement;
	protected void saveBookAuthorInfo(long bookId, long index, Author author) {
		if (myGetAuthorIdStatement == null) {
			myGetAuthorIdStatement = myDatabase.compileStatement(
				"SELECT author_id FROM Authors WHERE name = ? AND sort_key = ?"
			);
			myInsertAuthorStatement = myDatabase.compileStatement(
				"INSERT OR IGNORE INTO Authors (name,sort_key) VALUES (?,?)"
			);
			myInsertBookAuthorStatement = myDatabase.compileStatement(
				"INSERT OR REPLACE INTO BookAuthor (book_id,author_id,author_index) VALUES (?,?,?)"
			);
		}

		long authorId;
		try {
			myGetAuthorIdStatement.bindString(1, author.DisplayName);
			myGetAuthorIdStatement.bindString(2, author.SortKey);
			authorId = myGetAuthorIdStatement.simpleQueryForLong();
		} catch (SQLException e) {
			myInsertAuthorStatement.bindString(1, author.DisplayName);
			myInsertAuthorStatement.bindString(2, author.SortKey);
			authorId = myInsertAuthorStatement.executeInsert();
		}
		myInsertBookAuthorStatement.bindLong(1, bookId);
		myInsertBookAuthorStatement.bindLong(2, authorId);
		myInsertBookAuthorStatement.bindLong(3, index);
		myInsertBookAuthorStatement.execute();
	}

	protected List<Author> listAuthors(long bookId) {
		final Cursor cursor = myDatabase.rawQuery("SELECT Authors.name,Authors.sort_key FROM BookAuthor INNER JOIN Authors ON Authors.author_id = BookAuthor.author_id WHERE BookAuthor.book_id = ?", new String[] { String.valueOf(bookId) });
		if (!cursor.moveToNext()) {
			cursor.close();
			return null;
		}
		final ArrayList<Author> list = new ArrayList<Author>();
		do {
			list.add(new Author(cursor.getString(0), cursor.getString(1)));
		} while (cursor.moveToNext());
		cursor.close();
		return list;
	}

	private SQLiteStatement myGetTagIdStatement;
	private SQLiteStatement myCreateTagIdStatement;
	private long getTagId(Tag tag) {
		if (myGetTagIdStatement == null) {
			myGetTagIdStatement = myDatabase.compileStatement(
				"SELECT tag_id FROM Tags WHERE parent_id = ? AND name = ?"
			);
			myCreateTagIdStatement = myDatabase.compileStatement(
				"INSERT OR IGNORE INTO Tags (parent_id,name) VALUES (?,?)"
			);
		}
		{
			final Long id = myIdByTag.get(tag);
			if (id != null) {
				return id;
			}
		}
		if (tag.Parent != null) {
			myGetTagIdStatement.bindLong(1, getTagId(tag.Parent));
		} else {
			myGetTagIdStatement.bindNull(1);
		}
		myGetTagIdStatement.bindString(2, tag.Name);
		long id;
		try {
			id = myGetTagIdStatement.simpleQueryForLong();
		} catch (SQLException e) {
			if (tag.Parent != null) {
				myCreateTagIdStatement.bindLong(1, getTagId(tag.Parent));
			} else {
				myCreateTagIdStatement.bindNull(1);
			}
			myCreateTagIdStatement.bindString(2, tag.Name);
			id = myCreateTagIdStatement.executeInsert();
		}
		myIdByTag.put(tag, id);
		myTagById.put(id, tag);
		return id;
	}

	private SQLiteStatement myDeleteBookTagsStatement;
	protected void deleteAllBookTags(long bookId) {
		if (myDeleteBookTagsStatement == null) {
			myDeleteBookTagsStatement = myDatabase.compileStatement(
				"DELETE FROM BookTag WHERE book_id = ?"
			);
		}
		myDeleteBookTagsStatement.bindLong(1, bookId);
		myDeleteBookTagsStatement.execute();
	}

	private SQLiteStatement myInsertBookTagStatement;
	protected void saveBookTagInfo(long bookId, Tag tag) {
		if (myInsertBookTagStatement == null) {
			myInsertBookTagStatement = myDatabase.compileStatement(
				"INSERT OR IGNORE INTO BookTag (book_id,tag_id) VALUES (?,?)"
			);
		}
		myInsertBookTagStatement.bindLong(1, bookId);
		myInsertBookTagStatement.bindLong(2, getTagId(tag));
		myInsertBookTagStatement.execute();
	}

	private Tag getTagById(long id) {
		Tag tag = myTagById.get(id);
		if (tag == null) {
			final Cursor cursor = myDatabase.rawQuery("SELECT parent_id,name FROM Tags WHERE tag_id = ?", new String[] { String.valueOf(id) });
			if (cursor.moveToNext()) {
				final Tag parent = cursor.isNull(0) ? null : getTagById(cursor.getLong(0));
				tag = Tag.getTag(parent, cursor.getString(1));
				myIdByTag.put(tag, id);
				myTagById.put(id, tag);
			}
			cursor.close();
		}
		return tag;
	}

	protected List<Tag> listTags(long bookId) {
		final Cursor cursor = myDatabase.rawQuery("SELECT Tags.tag_id FROM BookTag INNER JOIN Tags ON Tags.tag_id = BookTag.tag_id WHERE BookTag.book_id = ?", new String[] { String.valueOf(bookId) });
		if (!cursor.moveToNext()) {
			cursor.close();
			return null;
		}
		final ArrayList<Tag> list = new ArrayList<Tag>();
		do {
			list.add(getTagById(cursor.getLong(0)));
		} while (cursor.moveToNext());
		cursor.close();
		return list;
	}

	@Override
	protected List<String> listLabels(long bookId) {
		final Cursor cursor = myDatabase.rawQuery(
			"SELECT Labels.name FROM Labels" +
			" INNER JOIN BookLabel ON BookLabel.label_id=Labels.label_id" +
			" WHERE BookLabel.book_id=?",
			new String[] { String.valueOf(bookId) }
		);
		final LinkedList<String> names = new LinkedList<String>();
		while (cursor.moveToNext()) {
			names.add(cursor.getString(0));
		}
		cursor.close();
		return names;
	}

	private SQLiteStatement myDeleteBookUidsStatement;
	protected void deleteAllBookUids(long bookId) {
		if (myDeleteBookUidsStatement == null) {
			myDeleteBookUidsStatement = myDatabase.compileStatement(
				"DELETE FROM BookUid WHERE book_id = ?"
			);
		}
		myDeleteBookUidsStatement.bindLong(1, bookId);
		myDeleteBookUidsStatement.execute();
	}

	private SQLiteStatement myInsertBookUidStatement;
	@Override
	protected void saveBookUid(long bookId, UID uid) {
		if (myInsertBookUidStatement == null) {
			myInsertBookUidStatement = myDatabase.compileStatement(
				"INSERT OR IGNORE INTO BookUid (book_id,type,uid) VALUES (?,?,?)"
			);
		}

		synchronized (myInsertBookUidStatement) {
			myInsertBookUidStatement.bindLong(1, bookId);
			myInsertBookUidStatement.bindString(2, uid.Type);
			myInsertBookUidStatement.bindString(3, uid.Id);
			myInsertBookUidStatement.execute();
		}
	}

	@Override
	protected List<UID> listUids(long bookId) {
		final ArrayList<UID> list = new ArrayList<UID>();
		final Cursor cursor = myDatabase.rawQuery("SELECT type,uid FROM BookUid WHERE book_id = ?", new String[] { String.valueOf(bookId) });
		while (cursor.moveToNext()) {
			list.add(new UID(cursor.getString(0), cursor.getString(1)));
		}
		cursor.close();
		return list;
	}

	@Override
	protected Long bookIdByUid(UID uid) {
		Long bookId = null;
		final Cursor cursor = myDatabase.rawQuery("SELECT book_id FROM BookUid WHERE type = ? AND uid = ?", new String[] { uid.Type, uid.Id });
		if (cursor.moveToNext()) {
			bookId = cursor.getLong(0);
		}
		cursor.close();
		return bookId;
	}

	private SQLiteStatement myGetSeriesIdStatement;
	private SQLiteStatement myInsertSeriesStatement;
	private SQLiteStatement myInsertBookSeriesStatement;
	private SQLiteStatement myDeleteBookSeriesStatement;
	protected void saveBookSeriesInfo(long bookId, SeriesInfo seriesInfo) {
		if (myGetSeriesIdStatement == null) {
			myGetSeriesIdStatement = myDatabase.compileStatement(
				"SELECT series_id FROM Series WHERE name = ?"
			);
			myInsertSeriesStatement = myDatabase.compileStatement(
				"INSERT OR IGNORE INTO Series (name) VALUES (?)"
			);
			myInsertBookSeriesStatement = myDatabase.compileStatement(
				"INSERT OR REPLACE INTO BookSeries (book_id,series_id,book_index) VALUES (?,?,?)"
			);
			myDeleteBookSeriesStatement = myDatabase.compileStatement(
				"DELETE FROM BookSeries WHERE book_id = ?"
			);
		}

		if (seriesInfo == null) {
			myDeleteBookSeriesStatement.bindLong(1, bookId);
			myDeleteBookSeriesStatement.execute();
		} else {
			long seriesId;
			try {
				myGetSeriesIdStatement.bindString(1, seriesInfo.Series.getTitle());
				seriesId = myGetSeriesIdStatement.simpleQueryForLong();
			} catch (SQLException e) {
				myInsertSeriesStatement.bindString(1, seriesInfo.Series.getTitle());
				seriesId = myInsertSeriesStatement.executeInsert();
			}
			myInsertBookSeriesStatement.bindLong(1, bookId);
			myInsertBookSeriesStatement.bindLong(2, seriesId);
			SQLiteUtil.bindString(
				myInsertBookSeriesStatement, 3,
				seriesInfo.Index != null ? seriesInfo.Index.toPlainString() : null
			);
			myInsertBookSeriesStatement.execute();
		}
	}

	protected SeriesInfo getSeriesInfo(long bookId) {
		final Cursor cursor = myDatabase.rawQuery("SELECT Series.name,BookSeries.book_index FROM BookSeries INNER JOIN Series ON Series.series_id = BookSeries.series_id WHERE BookSeries.book_id = ?", new String[] { String.valueOf(bookId) });
		SeriesInfo info = null;
		if (cursor.moveToNext()) {
			info = SeriesInfo.createSeriesInfo(cursor.getString(0), cursor.getString(1));
		}
		cursor.close();
		return info;
	}

	private SQLiteStatement myRemoveFileInfoStatement;
	protected void removeFileInfo(long fileId) {
		if (fileId == -1) {
			return;
		}
		if (myRemoveFileInfoStatement == null) {
			myRemoveFileInfoStatement = myDatabase.compileStatement(
				"DELETE FROM Files WHERE file_id = ?"
			);
		}
		myRemoveFileInfoStatement.bindLong(1, fileId);
		myRemoveFileInfoStatement.execute();
	}

	private SQLiteStatement myInsertFileInfoStatement;
	private SQLiteStatement myUpdateFileInfoStatement;
	protected void saveFileInfo(FileInfo fileInfo) {
		final long id = fileInfo.Id;
		SQLiteStatement statement;
		if (id == -1) {
			if (myInsertFileInfoStatement == null) {
				myInsertFileInfoStatement = myDatabase.compileStatement(
					"INSERT OR IGNORE INTO Files (name,parent_id,size) VALUES (?,?,?)"
				);
			}
			statement = myInsertFileInfoStatement;
		} else {
			if (myUpdateFileInfoStatement == null) {
				myUpdateFileInfoStatement = myDatabase.compileStatement(
					"UPDATE Files SET name = ?, parent_id = ?, size = ? WHERE file_id = ?"
				);
			}
			statement = myUpdateFileInfoStatement;
		}
		statement.bindString(1, fileInfo.Name);
		final FileInfo parent = fileInfo.Parent;
		if (parent != null) {
			statement.bindLong(2, parent.Id);
		} else {
			statement.bindNull(2);
		}
		final long size = fileInfo.FileSize;
		if (size != -1) {
			statement.bindLong(3, size);
		} else {
			statement.bindNull(3);
		}
		if (id == -1) {
			fileInfo.Id = statement.executeInsert();
		} else {
			statement.bindLong(4, id);
			statement.execute();
		}
	}

	protected Collection<FileInfo> loadFileInfos() {
		Cursor cursor = myDatabase.rawQuery(
			"SELECT file_id,name,parent_id,size FROM Files", null
		);
		HashMap<Long,FileInfo> infosById = new HashMap<Long,FileInfo>();
		while (cursor.moveToNext()) {
			final long id = cursor.getLong(0);
			final FileInfo info = createFileInfo(id,
				cursor.getString(1),
				cursor.isNull(2) ? null : infosById.get(cursor.getLong(2))
			);
			if (!cursor.isNull(3)) {
				info.FileSize = cursor.getLong(3);
			}
			infosById.put(id, info);
		}
		cursor.close();
		return infosById.values();
	}

	protected Collection<FileInfo> loadFileInfos(ZLFile file) {
		final LinkedList<ZLFile> fileStack = new LinkedList<ZLFile>();
		for (; file != null; file = file.getParent()) {
			fileStack.addFirst(file);
		}

		final ArrayList<FileInfo> infos = new ArrayList<FileInfo>(fileStack.size());
		final String[] parameters = { null };
		FileInfo current = null;
		for (ZLFile f : fileStack) {
			parameters[0] = f.getLongName();
			final Cursor cursor = myDatabase.rawQuery(
				(current == null) ?
					"SELECT file_id,size FROM Files WHERE name = ?" :
					"SELECT file_id,size FROM Files WHERE parent_id = " + current.Id + " AND name = ?",
				parameters
			);
			if (cursor.moveToNext()) {
				current = createFileInfo(cursor.getLong(0), parameters[0], current);
				if (!cursor.isNull(1)) {
					current.FileSize = cursor.getLong(1);
				}
				infos.add(current);
				cursor.close();
			} else {
				cursor.close();
				break;
			}
		}

		return infos;
	}

	protected Collection<FileInfo> loadFileInfos(long fileId) {
		final ArrayList<FileInfo> infos = new ArrayList<FileInfo>();
		while (fileId != -1) {
			final Cursor cursor = myDatabase.rawQuery(
				"SELECT name,size,parent_id FROM Files WHERE file_id = " + fileId, null
			);
			if (cursor.moveToNext()) {
				FileInfo info = createFileInfo(fileId, cursor.getString(0), null);
				if (!cursor.isNull(1)) {
					info.FileSize = cursor.getLong(1);
				}
				infos.add(0, info);
				fileId = cursor.isNull(2) ? -1 : cursor.getLong(2);
			} else {
				fileId = -1;
			}
			cursor.close();
		}
		for (int i = 1; i < infos.size(); ++i) {
			final FileInfo oldInfo = infos.get(i);
			final FileInfo newInfo = createFileInfo(oldInfo.Id, oldInfo.Name, infos.get(i - 1));
			newInfo.FileSize = oldInfo.FileSize;
			infos.set(i, newInfo);
		}
		return infos;
	}

	private SQLiteStatement mySaveRecentBookStatement;
	protected void saveRecentBookIds(final List<Long> ids) {
		if (mySaveRecentBookStatement == null) {
			mySaveRecentBookStatement = myDatabase.compileStatement(
				"INSERT OR IGNORE INTO RecentBooks (book_id) VALUES (?)"
			);
		}
		executeAsTransaction(new Runnable() {
			public void run() {
				myDatabase.delete("RecentBooks", null, null);
				for (long id : ids) {
					mySaveRecentBookStatement.bindLong(1, id);
					mySaveRecentBookStatement.execute();
				}
			}
		});
	}

	@Override
	protected List<Long> loadRecentBookIds() {
		final Cursor cursor = myDatabase.rawQuery(
			"SELECT book_id FROM RecentBooks ORDER BY book_index", null
		);
		final LinkedList<Long> ids = new LinkedList<Long>();
		while (cursor.moveToNext()) {
			ids.add(cursor.getLong(0));
		}
		cursor.close();
		return ids;
	}

	private SQLiteStatement mySetLabelStatement;
	@Override
	protected void setLabel(long bookId, String label) {
		myDatabase.execSQL("INSERT OR IGNORE INTO Labels (name) VALUES (?)", new Object[] { label });
		if (mySetLabelStatement == null) {
			mySetLabelStatement = myDatabase.compileStatement(
				"INSERT OR IGNORE INTO BookLabel(label_id,book_id)" +
				" SELECT label_id,? FROM Labels WHERE name=?"
			);
		}
		mySetLabelStatement.bindLong(1, bookId);
		mySetLabelStatement.bindString(2, label);
		mySetLabelStatement.execute();
	}

	private SQLiteStatement myRemoveLabelStatement;
	@Override
	protected void removeLabel(long bookId, String label) {
		if (myRemoveLabelStatement == null) {
			myRemoveLabelStatement = myDatabase.compileStatement(
				"DELETE FROM BookLabel WHERE book_id=? AND label_id IN" +
				" (SELECT label_id FROM Labels WHERE name=?)"
			);
		}
		myRemoveLabelStatement.bindLong(1, bookId);
		myRemoveLabelStatement.bindString(2, label);
		myRemoveLabelStatement.execute();
	}

	@Override
	protected boolean hasVisibleBookmark(long bookId) {
		final Cursor cursor = myDatabase.rawQuery(
			"SELECT bookmark_id FROM Bookmarks WHERE book_id = " + bookId +
			" AND visible = 1 LIMIT 1", null
		);
		final boolean result = cursor.moveToNext();
		cursor.close();
		return result;
	}

	@Override
	protected List<Bookmark> loadBookmarks(BookmarkQuery query) {
		final LinkedList<Bookmark> list = new LinkedList<Bookmark>();
		final StringBuilder sql = new StringBuilder("SELECT")
			.append(" bm.bookmark_id,bm.book_id,b.title,bm.bookmark_text,")
			.append("bm.creation_time,bm.modification_time,bm.access_time,bm.access_counter,")
			.append("bm.model_id,bm.paragraph,bm.word,bm.char,")
			.append("bm.end_paragraph,bm.end_word,bm.end_character,")
			.append("bm.style_id")
			.append(" FROM Bookmarks AS bm")
			.append(" INNER JOIN Books AS b ON b.book_id = bm.book_id")
			.append(" WHERE");
		if (query.Book != null) {
			sql.append(" b.book_id = " + query.Book.getId() +" AND");
		}
		sql
			.append(" bm.visible = " + (query.Visible ? 1 : 0))
			.append(" ORDER BY bm.bookmark_id")
			.append(" LIMIT " + query.Limit * query.Page + "," + query.Limit);
		Cursor cursor = myDatabase.rawQuery(sql.toString(), null);
		while (cursor.moveToNext()) {
			list.add(createBookmark(
				cursor.getLong(0),
				cursor.getLong(1),
				cursor.getString(2),
				cursor.getString(3),
				SQLiteUtil.getDate(cursor, 4),
				SQLiteUtil.getDate(cursor, 5),
				SQLiteUtil.getDate(cursor, 6),
				(int)cursor.getLong(7),
				cursor.getString(8),
				(int)cursor.getLong(9),
				(int)cursor.getLong(10),
				(int)cursor.getLong(11),
				(int)cursor.getLong(12),
				cursor.isNull(13) ? -1 : (int)cursor.getLong(13),
				cursor.isNull(14) ? -1 : (int)cursor.getLong(14),
				query.Visible,
				(int)cursor.getLong(15)
			));
		}
		cursor.close();
		return list;
	}

	@Override
	protected List<HighlightingStyle> loadStyles() {
		final LinkedList<HighlightingStyle> list = new LinkedList<HighlightingStyle>();
		final String sql = "SELECT style_id,name,bg_color FROM HighlightingStyle";
		final Cursor cursor = myDatabase.rawQuery(sql, null);
		while (cursor.moveToNext()) {
			list.add(createStyle(
				(int)cursor.getLong(0),
				cursor.getString(1),
				(int)cursor.getLong(2)
			));
		}
		cursor.close();
		return list;
	}

	private SQLiteStatement myInsertStyleStatement;
	protected void saveStyle(HighlightingStyle style) {
		if (myInsertStyleStatement == null) {
			myInsertStyleStatement = myDatabase.compileStatement(
				"INSERT OR REPLACE INTO HighlightingStyle (style_id,name,bg_color) VALUES (?,?,?)"
			);
		}
		myInsertStyleStatement.bindLong(1, style.Id);
		final String name = style.getName();
		myInsertStyleStatement.bindString(2, name != null ? name : "");
		final ZLColor bgColor = style.getBackgroundColor();
		myInsertStyleStatement.bindLong(3, bgColor != null ? bgColor.intValue() : -1);
		myInsertStyleStatement.executeInsert();
	}

	private SQLiteStatement myInsertBookmarkStatement;
	private SQLiteStatement myUpdateBookmarkStatement;
	@Override
	protected long saveBookmark(Bookmark bookmark) {
		SQLiteStatement statement;
		if (bookmark.getId() == -1) {
			if (myInsertBookmarkStatement == null) {
				myInsertBookmarkStatement = myDatabase.compileStatement(
					"INSERT OR IGNORE INTO Bookmarks (book_id,bookmark_text,creation_time,modification_time,access_time,access_counter,model_id,paragraph,word,char,end_paragraph,end_word,end_character,visible,style_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
				);
			}
			statement = myInsertBookmarkStatement;
		} else {
			if (myUpdateBookmarkStatement == null) {
				myUpdateBookmarkStatement = myDatabase.compileStatement(
					"UPDATE Bookmarks SET book_id = ?, bookmark_text = ?, creation_time =?, modification_time = ?,access_time = ?, access_counter = ?, model_id = ?, paragraph = ?, word = ?, char = ?, end_paragraph = ?, end_word = ?, end_character = ?, visible = ?, style_id = ? WHERE bookmark_id = ?"
				);
			}
			statement = myUpdateBookmarkStatement;
		}

		statement.bindLong(1, bookmark.getBookId());
		statement.bindString(2, bookmark.getText());
		SQLiteUtil.bindDate(statement, 3, bookmark.getDate(Bookmark.DateType.Creation));
		SQLiteUtil.bindDate(statement, 4, bookmark.getDate(Bookmark.DateType.Modification));
		SQLiteUtil.bindDate(statement, 5, bookmark.getDate(Bookmark.DateType.Access));
		statement.bindLong(6, bookmark.getAccessCount());
		SQLiteUtil.bindString(statement, 7, bookmark.ModelId);
		statement.bindLong(8, bookmark.ParagraphIndex);
		statement.bindLong(9, bookmark.ElementIndex);
		statement.bindLong(10, bookmark.CharIndex);
		final ZLTextPosition end = bookmark.getEnd();
		if (end != null) {
			statement.bindLong(11, end.getParagraphIndex());
			statement.bindLong(12, end.getElementIndex());
			statement.bindLong(13, end.getCharIndex());
		} else {
			statement.bindLong(11, bookmark.getLength());
			statement.bindNull(12);
			statement.bindNull(13);
		}
		statement.bindLong(14, bookmark.IsVisible ? 1 : 0);
		statement.bindLong(15, bookmark.getStyleId());

		if (statement == myInsertBookmarkStatement) {
			return statement.executeInsert();
		} else {
			final long id = bookmark.getId();
			statement.bindLong(16, id);
			statement.execute();
			return id;
		}
	}

	private SQLiteStatement myDeleteBookmarkStatement;
	@Override
	protected void deleteBookmark(Bookmark bookmark) {
		if (myDeleteBookmarkStatement == null) {
			myDeleteBookmarkStatement = myDatabase.compileStatement(
				"DELETE FROM Bookmarks WHERE bookmark_id = ?"
			);
		}
		myDeleteBookmarkStatement.bindLong(1, bookmark.getId());
		myDeleteBookmarkStatement.execute();
	}

	protected ZLTextPosition getStoredPosition(long bookId) {
		ZLTextPosition position = null;
		Cursor cursor = myDatabase.rawQuery(
			"SELECT paragraph,word,char FROM BookState WHERE book_id = " + bookId, null
		);
		if (cursor.moveToNext()) {
			position = new ZLTextFixedPosition(
				(int)cursor.getLong(0),
				(int)cursor.getLong(1),
				(int)cursor.getLong(2)
			);
		}
		cursor.close();
		return position;
	}

	private SQLiteStatement myStorePositionStatement;
	protected void storePosition(long bookId, ZLTextPosition position) {
		if (myStorePositionStatement == null) {
			myStorePositionStatement = myDatabase.compileStatement(
				"INSERT OR REPLACE INTO BookState (book_id,paragraph,word,char) VALUES (?,?,?,?)"
			);
		}
		myStorePositionStatement.bindLong(1, bookId);
		myStorePositionStatement.bindLong(2, position.getParagraphIndex());
		myStorePositionStatement.bindLong(3, position.getElementIndex());
		myStorePositionStatement.bindLong(4, position.getCharIndex());
		myStorePositionStatement.execute();
	}

	private SQLiteStatement myDeleteVisitedHyperlinksStatement;
	private void deleteVisitedHyperlinks(long bookId) {
		if (myDeleteVisitedHyperlinksStatement == null) {
			myDeleteVisitedHyperlinksStatement = myDatabase.compileStatement(
				"DELETE FROM VisitedHyperlinks WHERE book_id = ?"
			);
		}

		myDeleteVisitedHyperlinksStatement.bindLong(1, bookId);
		myDeleteVisitedHyperlinksStatement.execute();
	}

	private SQLiteStatement myStoreVisitedHyperlinksStatement;
	protected void addVisitedHyperlink(long bookId, String hyperlinkId) {
		if (myStoreVisitedHyperlinksStatement == null) {
			myStoreVisitedHyperlinksStatement = myDatabase.compileStatement(
				"INSERT OR IGNORE INTO VisitedHyperlinks(book_id,hyperlink_id) VALUES (?,?)"
			);
		}

		myStoreVisitedHyperlinksStatement.bindLong(1, bookId);
		myStoreVisitedHyperlinksStatement.bindString(2, hyperlinkId);
		myStoreVisitedHyperlinksStatement.execute();
	}

	protected Collection<String> loadVisitedHyperlinks(long bookId) {
		final TreeSet<String> links = new TreeSet<String>();
		final Cursor cursor = myDatabase.rawQuery("SELECT hyperlink_id FROM VisitedHyperlinks WHERE book_id = ?", new String[] { String.valueOf(bookId) });
		while (cursor.moveToNext()) {
			links.add(cursor.getString(0));
		}
		cursor.close();
		return links;
	}
	
	private SQLiteStatement mySaveProgessStatement;
	@Override
	protected void saveProgress(long bookId, RationalNumber progress) {
		if (mySaveProgessStatement == null) {
			mySaveProgessStatement = myDatabase.compileStatement(
				"INSERT OR REPLACE INTO BookReadingProgress (book_id,numerator,denominator) VALUES (?,?,?)"
			);
		}
		mySaveProgessStatement.bindLong(1, bookId);
		mySaveProgessStatement.bindLong(2, progress.Numerator);
		mySaveProgessStatement.bindLong(3, progress.Denominator);
		mySaveProgessStatement.execute();
	}

	@Override
	protected RationalNumber loadProgress(long bookId) {
		final RationalNumber progress;
		final Cursor cursor = myDatabase.rawQuery(
			"SELECT numerator,denominator FROM BookReadingProgress WHERE book_id = " + bookId, null
		);
		if (cursor.moveToNext()) {
			progress = new RationalNumber(cursor.getLong(0), cursor.getLong(1));
		} else {
			progress = new RationalNumber(0, 1);
		}
		cursor.close();
		return progress;
	}

	private void createTables() {
		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS Books(" +
				"book_id INTEGER PRIMARY KEY," +
				"encoding TEXT," +
				"language TEXT," +
				"title TEXT NOT NULL," +
				"file_name TEXT UNIQUE NOT NULL)");
		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS Authors(" +
				"author_id INTEGER PRIMARY KEY," +
				"name TEXT NOT NULL," +
				"sort_key TEXT NOT NULL," +
				"CONSTRAINT Authors_Unique UNIQUE (name, sort_key))");
		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS BookAuthor(" +
				"author_id INTEGER NOT NULL REFERENCES Authors(author_id)," +
				"book_id INTEGER NOT NULL REFERENCES Books(book_id)," +
				"author_index INTEGER NOT NULL," +
				"CONSTRAINT BookAuthor_Unique0 UNIQUE (author_id, book_id)," +
				"CONSTRAINT BookAuthor_Unique1 UNIQUE (book_id, author_index))");
		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS Series(" +
				"series_id INTEGER PRIMARY KEY," +
				"name TEXT UNIQUE NOT NULL)");
		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS BookSeries(" +
				"series_id INTEGER NOT NULL REFERENCES Series(series_id)," +
				"book_id INTEGER NOT NULL UNIQUE REFERENCES Books(book_id)," +
				"book_index INTEGER)");
		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS Tags(" +
				"tag_id INTEGER PRIMARY KEY," +
				"name TEXT NOT NULL," +
				"parent INTEGER REFERENCES Tags(tag_id)," +
				"CONSTRAINT Tags_Unique UNIQUE (name, parent))");
		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS BookTag(" +
				"tag_id INTEGER REFERENCES Tags(tag_id)," +
				"book_id INTEGER REFERENCES Books(book_id)," +
				"CONSTRAINT BookTag_Unique UNIQUE (tag_id, book_id))");
	}

	private void updateTables1() {
		myDatabase.execSQL("ALTER TABLE Tags RENAME TO Tags_Obsolete");
		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS Tags(" +
				"tag_id INTEGER PRIMARY KEY," +
				"name TEXT NOT NULL," +
				"parent_id INTEGER REFERENCES Tags(tag_id)," +
				"CONSTRAINT Tags_Unique UNIQUE (name, parent_id))");
		myDatabase.execSQL("INSERT INTO Tags (tag_id,name,parent_id) SELECT tag_id,name,parent FROM Tags_Obsolete");
		myDatabase.execSQL("DROP TABLE IF EXISTS Tags_Obsolete");

		myDatabase.execSQL("ALTER TABLE BookTag RENAME TO BookTag_Obsolete");
		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS BookTag(" +
				"tag_id INTEGER NOT NULL REFERENCES Tags(tag_id)," +
				"book_id INTEGER NOT NULL REFERENCES Books(book_id)," +
				"CONSTRAINT BookTag_Unique UNIQUE (tag_id, book_id))");
		myDatabase.execSQL("INSERT INTO BookTag (tag_id,book_id) SELECT tag_id,book_id FROM BookTag_Obsolete");
		myDatabase.execSQL("DROP TABLE IF EXISTS BookTag_Obsolete");
	}

	private void updateTables2() {
		myDatabase.execSQL("CREATE INDEX BookAuthor_BookIndex ON BookAuthor (book_id)");
		myDatabase.execSQL("CREATE INDEX BookTag_BookIndex ON BookTag (book_id)");
		myDatabase.execSQL("CREATE INDEX BookSeries_BookIndex ON BookSeries (book_id)");
	}

	private void updateTables3() {
		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS Files(" +
				"file_id INTEGER PRIMARY KEY," +
				"name TEXT NOT NULL," +
				"parent_id INTEGER REFERENCES Files(file_id)," +
				"size INTEGER," +
				"CONSTRAINT Files_Unique UNIQUE (name, parent_id))");
	}

	private void updateTables4() {
		final FileInfoSet fileInfos = new FileInfoSet(this);
		final Cursor cursor = myDatabase.rawQuery(
			"SELECT file_name FROM Books", null
		);
		while (cursor.moveToNext()) {
			fileInfos.check(ZLFile.createFileByPath(cursor.getString(0)).getPhysicalFile(), false);
		}
		cursor.close();
		fileInfos.save();

		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS RecentBooks(" +
				"book_index INTEGER PRIMARY KEY," +
				"book_id INTEGER REFERENCES Books(book_id))");
		final ArrayList<Long> ids = new ArrayList<Long>();

		final SQLiteStatement statement = myDatabase.compileStatement(
			"SELECT book_id FROM Books WHERE file_name = ?"
		);

		for (int i = 0; i < 20; ++i) {
			final ZLStringOption option = new ZLStringOption("LastOpenedBooks", "Book" + i, "");
			final String fileName = option.getValue();
			option.setValue("");
			try {
				statement.bindString(1, fileName);
				final long bookId = statement.simpleQueryForLong();
				if (bookId != -1) {
					ids.add(bookId);
				}
			} catch (SQLException e) {
			}
		}
		saveRecentBookIds(ids);
	}

	private void updateTables5() {
		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS Bookmarks(" +
				"bookmark_id INTEGER PRIMARY KEY," +
				"book_id INTEGER NOT NULL REFERENCES Books(book_id)," +
				"bookmark_text TEXT NOT NULL," +
				"creation_time INTEGER NOT NULL," +
				"modification_time INTEGER," +
				"access_time INTEGER," +
				"access_counter INTEGER NOT NULL," +
				"paragraph INTEGER NOT NULL," +
				"word INTEGER NOT NULL," +
				"char INTEGER NOT NULL)");

		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS BookState(" +
				"book_id INTEGER UNIQUE NOT NULL REFERENCES Books(book_id)," +
				"paragraph INTEGER NOT NULL," +
				"word INTEGER NOT NULL," +
				"char INTEGER NOT NULL)");
		Cursor cursor = myDatabase.rawQuery(
			"SELECT book_id,file_name FROM Books", null
		);
		final SQLiteStatement statement = myDatabase.compileStatement("INSERT INTO BookState (book_id,paragraph,word,char) VALUES (?,?,?,?)");
		while (cursor.moveToNext()) {
			final long bookId = cursor.getLong(0);
			final String fileName = cursor.getString(1);
			final int position = new ZLIntegerOption(fileName, "PositionInBuffer", 0).getValue();
			final int paragraph = new ZLIntegerOption(fileName, "Paragraph_" + position, 0).getValue();
			final int word = new ZLIntegerOption(fileName, "Word_" + position, 0).getValue();
			final int chr = new ZLIntegerOption(fileName, "Char_" + position, 0).getValue();
			if ((paragraph != 0) || (word != 0) || (chr != 0)) {
				statement.bindLong(1, bookId);
				statement.bindLong(2, paragraph);
				statement.bindLong(3, word);
				statement.bindLong(4, chr);
				statement.execute();
			}
			ZLConfig.Instance().removeGroup(fileName);
		}
		cursor.close();
	}

	private void updateTables6() {
		myDatabase.execSQL(
			"ALTER TABLE Bookmarks ADD COLUMN model_id TEXT"
		);

		myDatabase.execSQL(
			"ALTER TABLE Books ADD COLUMN file_id INTEGER"
		);

		myDatabase.execSQL("DELETE FROM Files");
		final FileInfoSet infoSet = new FileInfoSet(this);
		Cursor cursor = myDatabase.rawQuery(
			"SELECT file_name FROM Books", null
		);
		while (cursor.moveToNext()) {
			infoSet.check(ZLFile.createFileByPath(cursor.getString(0)).getPhysicalFile(), false);
		}
		cursor.close();
		infoSet.save();

		cursor = myDatabase.rawQuery(
			"SELECT book_id,file_name FROM Books", null
		);
		final SQLiteStatement deleteStatement = myDatabase.compileStatement("DELETE FROM Books WHERE book_id = ?");
		final SQLiteStatement updateStatement = myDatabase.compileStatement("UPDATE OR IGNORE Books SET file_id = ? WHERE book_id = ?");
		while (cursor.moveToNext()) {
			final long bookId = cursor.getLong(0);
			final long fileId = infoSet.getId(ZLFile.createFileByPath(cursor.getString(1)));

			if (fileId == -1) {
				deleteStatement.bindLong(1, bookId);
				deleteStatement.execute();
			} else {
				updateStatement.bindLong(1, fileId);
				updateStatement.bindLong(2, bookId);
				updateStatement.execute();
			}
		}
		cursor.close();

		myDatabase.execSQL("ALTER TABLE Books RENAME TO Books_Obsolete");
		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS Books(" +
				"book_id INTEGER PRIMARY KEY," +
				"encoding TEXT," +
				"language TEXT," +
				"title TEXT NOT NULL," +
				"file_id INTEGER UNIQUE NOT NULL REFERENCES Files(file_id))");
		myDatabase.execSQL("INSERT INTO Books (book_id,encoding,language,title,file_id) SELECT book_id,encoding,language,title,file_id FROM Books_Obsolete");
		myDatabase.execSQL("DROP TABLE IF EXISTS Books_Obsolete");
	}

	private void updateTables7() {
		final ArrayList<Long> seriesIDs = new ArrayList<Long>();
		Cursor cursor = myDatabase.rawQuery(
			"SELECT series_id,name FROM Series", null
		);
		while (cursor.moveToNext()) {
			if (cursor.getString(1).length() > 200) {
				seriesIDs.add(cursor.getLong(0));
			}
		}
		cursor.close();
		if (seriesIDs.isEmpty()) {
			return;
		}

		final ArrayList<Long> bookIDs = new ArrayList<Long>();
		for (Long id : seriesIDs) {
			cursor = myDatabase.rawQuery(
				"SELECT book_id FROM BookSeries WHERE series_id=" + id, null
			);
			while (cursor.moveToNext()) {
				bookIDs.add(cursor.getLong(0));
			}
			cursor.close();
			myDatabase.execSQL("DELETE FROM BookSeries WHERE series_id=" + id);
			myDatabase.execSQL("DELETE FROM Series WHERE series_id=" + id);
		}

		for (Long id : bookIDs) {
			myDatabase.execSQL("DELETE FROM Books WHERE book_id=" + id);
			myDatabase.execSQL("DELETE FROM BookAuthor WHERE book_id=" + id);
			myDatabase.execSQL("DELETE FROM BookTag WHERE book_id=" + id);
		}
	}

	private void updateTables8() {
		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS BookList ( " +
				"book_id INTEGER UNIQUE NOT NULL REFERENCES Books (book_id))");
	}

	private void updateTables9() {
		myDatabase.execSQL("CREATE INDEX BookList_BookIndex ON BookList (book_id)");
	}

	private void updateTables10() {
		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS Favorites(" +
				"book_id INTEGER UNIQUE NOT NULL REFERENCES Books(book_id))");
	}

	private void updateTables11() {
		myDatabase.execSQL("UPDATE Files SET size = size + 1");
	}

	private void updateTables12() {
		myDatabase.execSQL("DELETE FROM Files WHERE parent_id IN (SELECT file_id FROM Files WHERE name LIKE '%.epub')");
	}

	private void updateTables13() {
		myDatabase.execSQL(
			"ALTER TABLE Bookmarks ADD COLUMN visible INTEGER DEFAULT 1"
		);
	}

	private void updateTables14() {
		myDatabase.execSQL("ALTER TABLE BookSeries RENAME TO BookSeries_Obsolete");
		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS BookSeries(" +
				"series_id INTEGER NOT NULL REFERENCES Series(series_id)," +
				"book_id INTEGER NOT NULL UNIQUE REFERENCES Books(book_id)," +
				"book_index REAL)");
		myDatabase.execSQL("INSERT INTO BookSeries (series_id,book_id,book_index) SELECT series_id,book_id,book_index FROM BookSeries_Obsolete");
		myDatabase.execSQL("DROP TABLE IF EXISTS BookSeries_Obsolete");
	}

	private void updateTables15() {
		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS VisitedHyperlinks(" +
				"book_id INTEGER NOT NULL REFERENCES Books(book_id)," +
				"hyperlink_id TEXT NOT NULL," +
				"CONSTRAINT VisitedHyperlinks_Unique UNIQUE (book_id, hyperlink_id))");
	}

	private void updateTables16() {
		myDatabase.execSQL(
			"ALTER TABLE Books ADD COLUMN `exists` INTEGER DEFAULT 1"
		);
	}

	private void updateTables17() {
		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS BookStatus(" +
				"book_id INTEGER NOT NULL REFERENCES Books(book_id) PRIMARY KEY," +
				"access_time INTEGER NOT NULL," +
				"pages_full INTEGER NOT NULL," +
				"page_current INTEGER NOT NULL)");
	}

	private void updateTables18() {
		myDatabase.execSQL("ALTER TABLE BookSeries RENAME TO BookSeries_Obsolete");
		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS BookSeries(" +
				"series_id INTEGER NOT NULL REFERENCES Series(series_id)," +
				"book_id INTEGER NOT NULL UNIQUE REFERENCES Books(book_id)," +
				"book_index TEXT)");
		final SQLiteStatement insert = myDatabase.compileStatement(
			"INSERT INTO BookSeries (series_id,book_id,book_index) VALUES (?,?,?)"
		);
		final Cursor cursor = myDatabase.rawQuery("SELECT series_id,book_id,book_index FROM BookSeries_Obsolete", null);
		while (cursor.moveToNext()) {
			insert.bindLong(1, cursor.getLong(0));
			insert.bindLong(2, cursor.getLong(1));
			final float index = cursor.getFloat(2);
			final String stringIndex;
			if (index == 0.0f) {
				stringIndex = null;
			} else {
				if (Math.abs(index - Math.round(index)) < 0.01) {
					stringIndex = String.valueOf(Math.round(index));
				} else {
					stringIndex = String.format("%.1f", index);
				}
			}
			final BigDecimal bdIndex = SeriesInfo.createIndex(stringIndex);
			SQLiteUtil.bindString(insert, 3, bdIndex != null ? bdIndex.toString() : null);
			insert.executeInsert();
		}
		cursor.close();
		myDatabase.execSQL("DROP TABLE IF EXISTS BookSeries_Obsolete");
	}

	private void updateTables19() {
		myDatabase.execSQL("DROP TABLE IF EXISTS BookList");
	}

	private void updateTables20() {
		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS Labels(" +
				"label_id INTEGER PRIMARY KEY," +
				"name TEXT NOT NULL UNIQUE)");
		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS BookLabel(" +
				"label_id INTEGER NOT NULL REFERENCES Labels(label_id)," +
				"book_id INTEGER NOT NULL REFERENCES Books(book_id)," +
				"CONSTRAINT BookLabel_Unique UNIQUE (label_id,book_id))");
		final SQLiteStatement insert = myDatabase.compileStatement(
			"INSERT INTO Labels (name) VALUES ('favorite')"
		);
		final long id = insert.executeInsert();
		myDatabase.execSQL("INSERT INTO BookLabel (label_id,book_id) SELECT " + id + ",book_id FROM Favorites");
		myDatabase.execSQL("DROP TABLE IF EXISTS Favorites");
	}

	private void updateTables21() {
		myDatabase.execSQL("DROP TABLE IF EXISTS BookUid");
		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS BookUid(" +
				"book_id INTEGER NOT NULL UNIQUE REFERENCES Books(book_id)," +
				"type TEXT NOT NULL," +
				"uid TEXT NOT NULL," +
				"CONSTRAINT BookUid_Unique UNIQUE (book_id,type,uid))");
	}

	private void updateTables22() {
		myDatabase.execSQL("ALTER TABLE Bookmarks ADD COLUMN end_paragraph INTEGER");
		myDatabase.execSQL("ALTER TABLE Bookmarks ADD COLUMN end_word INTEGER");
		myDatabase.execSQL("ALTER TABLE Bookmarks ADD COLUMN end_character INTEGER");
	}

	private void updateTables23() {
		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS HighlightingStyle(" +
				"style_id INTEGER PRIMARY KEY," +
				"name TEXT NOT NULL," +
				"bg_color INTEGER NOT NULL)");
		myDatabase.execSQL("ALTER TABLE Bookmarks ADD COLUMN style_id INTEGER NOT NULL REFERENCES HighlightingStyle(style_id) DEFAULT 1");
		myDatabase.execSQL("UPDATE Bookmarks SET end_paragraph = LENGTH(bookmark_text)");
	}

	private void updateTables24() {
		myDatabase.execSQL("INSERT OR REPLACE INTO HighlightingStyle (style_id, name, bg_color) VALUES (1, '', 136*256*256 + 138*256 + 133)"); // #888a85
		myDatabase.execSQL("INSERT OR REPLACE INTO HighlightingStyle (style_id, name, bg_color) VALUES (2, '', 245*256*256 + 121*256 + 0)"); // #f57900
		myDatabase.execSQL("INSERT OR REPLACE INTO HighlightingStyle (style_id, name, bg_color) VALUES (3, '', 114*256*256 + 159*256 + 207)"); // #729fcf
	}
	
	private void updateTables25() {
		myDatabase.execSQL(
			"CREATE TABLE IF NOT EXISTS BookReadingProgress(" +
				"book_id INTEGER PRIMARY KEY NOT NULL UNIQUE REFERENCES Books(book_id)," +
				"numerator INTEGER NOT NULL," +
				"denominator INTEGER NOT NULL)");
	}
}
