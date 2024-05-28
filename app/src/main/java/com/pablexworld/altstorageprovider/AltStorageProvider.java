// Contains GPLv3-licensed code from the Termux project.
// https://github.com/termux/termux-app/blob/master/app/src/main/java/com/termux/filepicker/TermuxDocumentsProvider.java

package com.pablexworld.altstorageprovider;

import android.content.ContentProvider;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Point;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.webkit.MimeTypeMap;

import org.chickenhook.restrictionbypass.RestrictionBypass;
import org.chickenhook.restrictionbypass.Unseal;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Objects;

/**
 * A document provider for the Storage Access Framework which exposes the files in the
 * $HOME/ directory to other apps.
 * <p/>
 * Note that this replaces providing an activity matching the ACTION_GET_CONTENT intent:
 * <p/>
 * "A document provider and ACTION_GET_CONTENT should be considered mutually exclusive. If you
 * support both of them simultaneously, your app will appear twice in the system picker UI,
 * offering two different ways of accessing your stored data. This would be confusing for users."
 * - <a href="http://developer.android.com/guide/topics/providers/document-provider.html#43">...</a>
 */
public class AltStorageProvider extends DocumentsProvider {

    private static final String ALL_MIME_TYPES = "*/*";

    private static final File BASE_DIR = new File("/storage/emulated/0");


    // The default columns to return information about a root if no specific
    // columns are requested in a query.
    private static final String[] DEFAULT_ROOT_PROJECTION = new String[]{
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_AVAILABLE_BYTES
    };

    // The default columns to return information about a document if no specific
    // columns are requested in a query.
    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[]{
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE
    };

    @Override
    public Cursor queryRoots(String[] projection) {
        final MatrixCursor result = new MatrixCursor(projection != null ? projection : DEFAULT_ROOT_PROJECTION);
        final String applicationName = "Internal Storage";

        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(Root.COLUMN_ROOT_ID, getDocIdForFile(BASE_DIR));
        row.add(Root.COLUMN_DOCUMENT_ID, getDocIdForFile(BASE_DIR));
        row.add(Root.COLUMN_SUMMARY, null);
        row.add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE | Root.FLAG_SUPPORTS_SEARCH | Root.FLAG_SUPPORTS_IS_CHILD);
        row.add(Root.COLUMN_TITLE, applicationName);
        row.add(Root.COLUMN_MIME_TYPES, ALL_MIME_TYPES);
        row.add(Root.COLUMN_AVAILABLE_BYTES, BASE_DIR.getFreeSpace());
        row.add(Root.COLUMN_ICON, R.mipmap.ic_launcher);
        return result;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION);
        includeFile(result, documentId, null);
        return result;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION);
        final File parent = getFileForDocId(parentDocumentId);
        for (File file : Objects.requireNonNull(parent.listFiles())) {
            includeFile(result, null, file);
        }
        return result;
    }

    @Override
    public ParcelFileDescriptor openDocument(final String documentId, String mode, CancellationSignal signal) throws FileNotFoundException {
        final File file = getFileForDocId(documentId);
        final int accessMode = ParcelFileDescriptor.parseMode(mode);
        return ParcelFileDescriptor.open(file, accessMode);
    }

    @Override
    public AssetFileDescriptor openDocumentThumbnail(String documentId, Point sizeHint, CancellationSignal signal) throws FileNotFoundException {
        final File file = getFileForDocId(documentId);
        final ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(pfd, 0, file.length());
    }

    // Bypass MANAGE_DOCUMENTS and other OS sanity checks by hacking around it with
    // voodoo reflection && stack bullshit.
    //
    // I'm going to programmer hell for this.
    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        // registerAuthority(info.authority)
        try {
            Unseal.unseal();
            Method m = RestrictionBypass.getDeclaredMethod(
                    DocumentsProvider.class, "registerAuthority",
                    String.class
            );
            if (!m.isAccessible()) {
                m.setAccessible(true);
            }
            m.invoke(this, info.authority);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // super.super.attachInfo(context, info);
        // https://stackoverflow.com/questions/586363/why-is-super-super-method-not-allowed-in-java
        try {
            ContentProviderSkel type = new ContentProviderSkel();
            shareVars(this, type);
            RestrictionBypass.getDeclaredMethod(
                    ContentProvider.class, "attachInfo",
                    Context.class,
                    ProviderInfo.class
            ).invoke(type, context, info);
            shareVars(type, this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Copy the values of fields, regardless of access, from one instance to another,
    // using reflection.
    private static <SourceType, TargetType> void shareVars(SourceType source, TargetType target)
            throws IllegalArgumentException, IllegalAccessException {
        Class<?> loop = ContentProvider.class;
        do {
            assert loop != null;
            for (Field f : loop.getDeclaredFields()) {
                if (!f.isAccessible()) {
                    f.setAccessible(true);
                }
                f.set(target, f.get(source));
            }
            loop = loop.getSuperclass();
        } while (loop != Object.class);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String createDocument(String parentDocumentId, String mimeType, String displayName) throws FileNotFoundException {
        File newFile = new File(parentDocumentId, displayName);
        int noConflictId = 2;
        while (newFile.exists()) {
            newFile = new File(parentDocumentId, displayName + " (" + noConflictId++ + ")");
        }
        try {
            boolean succeeded;
            if (Document.MIME_TYPE_DIR.equals(mimeType)) {
                succeeded = newFile.mkdir();
            } else {
                succeeded = newFile.createNewFile();
            }
            if (!succeeded) {
                throw new FileNotFoundException("Failed to create document with id " + newFile.getPath());
            }
        } catch (IOException e) {
            throw new FileNotFoundException("Failed to create document with id " + newFile.getPath());
        }
        return newFile.getPath();
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        File file = getFileForDocId(documentId);
        if (!file.delete()) {
            throw new FileNotFoundException("Failed to delete document with id " + documentId);
        }
    }

    @Override
    public String getDocumentType(String documentId) throws FileNotFoundException {
        File file = getFileForDocId(documentId);
        return getMimeType(file);
    }

    @Override
    public Cursor querySearchDocuments(String rootId, String query, String[] projection) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION);
        final File parent = getFileForDocId(rootId);

        // This example implementation searches file names for the query and doesn't rank search
        // results, so we can stop as soon as we find a sufficient number of matches.  Other
        // implementations might rank results and use other data about files, rather than the file
        // name, to produce a match.
        final LinkedList<File> pending = new LinkedList<>();
        pending.add(parent);

        final int MAX_SEARCH_RESULTS = 50;
        while (!pending.isEmpty() && result.getCount() < MAX_SEARCH_RESULTS) {
            final File file = pending.removeFirst();
            if (file.isDirectory()) {
                Collections.addAll(pending, Objects.requireNonNull(file.listFiles()));
            } else {
                if (file.getName().toLowerCase().contains(query)) {
                    includeFile(result, null, file);
                }
            }
        }

        return result;
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        return documentId.startsWith(parentDocumentId);
    }

    /**
     * Get the document id given a file. This document id must be consistent across time as other
     * applications may save the ID and use it to reference documents later.
     * <p/>
     * The reverse of @{link #getFileForDocId}.
     */
    private static String getDocIdForFile(File file) {
        return file.getAbsolutePath();
    }

    /**
     * Get the file given a document id (the reverse of {@link #getDocIdForFile(File)}).
     */
    private static File getFileForDocId(String docId) throws FileNotFoundException {
        final File f = new File(docId);
        if (!f.exists()) throw new FileNotFoundException(f.getAbsolutePath() + " not found");
        return f;
    }

    private static String getMimeType(File file) {
        if (file.isDirectory()) {
            return Document.MIME_TYPE_DIR;
        } else {
            final String name = file.getName();
            final int lastDot = name.lastIndexOf('.');
            if (lastDot >= 0) {
                final String extension = name.substring(lastDot + 1).toLowerCase();
                final String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                if (mime != null) return mime;
            }
            return "application/octet-stream";
        }
    }

    /**
     * Add a representation of a file to a cursor.
     *
     * @param result the cursor to modify
     * @param docId  the document ID representing the desired file (may be null if given file)
     * @param file   the File object representing the desired file (may be null if given docID)
     */
    private void includeFile(MatrixCursor result, String docId, File file)
            throws FileNotFoundException {
        if (docId == null) {
            docId = getDocIdForFile(file);
        } else {
            file = getFileForDocId(docId);
        }

        int flags = 0;
        if (file.isDirectory()) {
            if (file.canWrite()) flags |= Document.FLAG_DIR_SUPPORTS_CREATE;
        } else if (file.canWrite()) {
            flags |= Document.FLAG_SUPPORTS_WRITE;
        }
        if (Objects.requireNonNull(file.getParentFile()).canWrite()) flags |= Document.FLAG_SUPPORTS_DELETE;

        final String displayName = file.getName();
        final String mimeType = getMimeType(file);
        if (mimeType.startsWith("image/")) flags |= Document.FLAG_SUPPORTS_THUMBNAIL;

        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, docId);
        row.add(Document.COLUMN_DISPLAY_NAME, displayName);
        row.add(Document.COLUMN_SIZE, file.length());
        row.add(Document.COLUMN_MIME_TYPE, mimeType);
        row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified());
        row.add(Document.COLUMN_FLAGS, flags);
        row.add(Document.COLUMN_ICON, R.mipmap.ic_launcher);
    }

}