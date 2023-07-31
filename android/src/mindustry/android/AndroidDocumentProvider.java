package mindustry.android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import android.webkit.MimeTypeMap;

//import androidx.annotation.RequiresApi;
import io.anuke.mindustry.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

//@RequiresApi(api = Build.VERSION_CODES.KITKAT)
@SuppressLint("NewApi")
public class AndroidDocumentProvider extends DocumentsProvider {
    private static final String[] DEFAULT_ROOT_PROJECTION = new String[]{
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
    };
    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[]{
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
    };
    private static final String ROOT = "mindustryxuper";
    private static final String ROOT_DOCUMENT_ID = "mindustryxuper:";

    //private static final String DATA_PATH = "/data/data/xyz.yldk.io.anuke.mindustry/";
    //private final String DATA_PATH = getContext().getApplicationInfo().dataDir + "/";
    private String DATA_PATH = null;
    //private final String DATA_PATH = getContext().getExternalFilesDir(null).getAbsolutePath();


    @Override
    public boolean onCreate() {
        /*File[] externalFilesDirs = getContext().getExternalFilesDirs(null);
        for (File file : externalFilesDirs) {
            if (file != null && Environment.isExternalStorageRemovable(file)) {
                String sdCardDataPath = file.getAbsolutePath();
                this.DATA_PATH = sdCardDataPath;
            }
        }*/
        DATA_PATH = getContext().getExternalFilesDir(null).getAbsolutePath();

        return true;
    }



    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(resolveRootProjection(projection));
        MatrixCursor.RowBuilder row = result.newRow();
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT);
        row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT_DOCUMENT_ID);
        row.add(DocumentsContract.Root.COLUMN_TITLE, getContext().getString(R.string.files_title));
        row.add(DocumentsContract.Root.COLUMN_SUMMARY, getContext().getString(R.string.files_summary));
        row.add(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_SUPPORTS_CREATE | DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD);
        row.add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher);
        return result;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
        includeFile(result, documentId);
        return result;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(resolveDocumentProjection(projection));
        final File parent = getFileForDocId(parentDocumentId);
        for (File file : parent.listFiles()) {
            includeFile(result, getDocIdForFile(file));
        }
        return result;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal) throws FileNotFoundException {
        final File file = getFileForDocId(documentId);
        final int accessMode = ParcelFileDescriptor.parseMode(mode);
        return ParcelFileDescriptor.open(file, accessMode);
    }

    private void includeFile(MatrixCursor result, String documentId) throws FileNotFoundException {
        final File file = getFileForDocId(documentId);
        if (!file.exists()) {
            return;
        }
        int flags = 0;
        if (file.isDirectory()) {
            flags |= DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE;
        } else if (file.canWrite()) {
            flags |= DocumentsContract.Document.FLAG_SUPPORTS_WRITE;
            flags |= DocumentsContract.Document.FLAG_SUPPORTS_DELETE;
        }
        final String displayName = file.getName();
        final String mimeType = getMimeType(file);
        if (mimeType.startsWith("image/")) {
            flags |= DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL;
        }
        final MatrixCursor.RowBuilder row = result.newRow();
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId);
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName);
        row.add(DocumentsContract.Document.COLUMN_SIZE, file.length());
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeType);
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified());
        row.add(DocumentsContract.Document.COLUMN_FLAGS, flags);
    }

    private File getFileForDocId(String documentId) throws FileNotFoundException {
        File target = new File(DATA_PATH).getAbsoluteFile();
        if (documentId.equals(ROOT_DOCUMENT_ID)) {
            return target;
        } else if (documentId.startsWith(ROOT_DOCUMENT_ID)) {
            final String[] split = documentId.split(":");
            final String path = split[1];
            target = new File(target, path);
            if (!target.exists()) {
                throw new FileNotFoundException("Missing file for " + documentId + " at " + target);
            }
            return target;
        } else {
            throw new FileNotFoundException("Missing root for " + documentId);
        }
    }

    private String getDocIdForFile(File file) throws FileNotFoundException {
        String path = file.getAbsolutePath();
        final File root = new File(DATA_PATH);
        if (path.equals(root.getAbsolutePath())) {
            return ROOT_DOCUMENT_ID;
        } else if (path.startsWith(root.getAbsolutePath())) {
            return ROOT_DOCUMENT_ID + path.substring(root.getAbsolutePath().length());
        } else {
            throw new FileNotFoundException("Failed to find root that contains " + path);
        }
    }

    private String getMimeType(File file) {
        if (file.isDirectory()) {
            return DocumentsContract.Document.MIME_TYPE_DIR;
        } else {
            final String name = file.getName();
            final int lastDot = name.lastIndexOf('.');
            if (lastDot >= 0) {
                final String extension = name.substring(lastDot + 1);
                final String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                if (mime != null) {
                    return mime;
                }
            }
            return "application/octet-stream";
        }
    }

    private String[] resolveRootProjection(String[] projection) {
        return projection != null ? projection : DEFAULT_ROOT_PROJECTION;
    }

    private String[] resolveDocumentProjection(String[] projection) {
        return projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION;
    }
}
