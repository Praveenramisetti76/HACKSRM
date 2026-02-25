import { Router, Request, Response } from 'express';
import multer from 'multer';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';
import { uploadFile, waitForMSPConfirmOnChain, waitForBackendFileReady } from '../operations/fileOps.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Configure multer to save uploads to 'uploads/' directory
const uploadsDir = path.resolve(__dirname, '../../uploads');
if (!fs.existsSync(uploadsDir)) {
    fs.mkdirSync(uploadsDir, { recursive: true });
}

const storage = multer.diskStorage({
    destination: (_req, _file, cb) => cb(null, uploadsDir),
    filename: (_req, file, cb) => {
        // Unique filename: timestamp-originalname
        const uniqueName = `${Date.now()}-${file.originalname}`;
        cb(null, uniqueName);
    },
});

const upload = multer({
    storage,
    limits: {
        fileSize: 10 * 1024 * 1024, // 10 MB max
    },
    fileFilter: (_req, file, cb) => {
        // Accept images only (prescription photos)
        const allowedMimes = [
            'image/jpeg',
            'image/png',
            'image/webp',
            'image/gif',
            'application/pdf',
        ];
        if (allowedMimes.includes(file.mimetype)) {
            cb(null, true);
        } else {
            cb(new Error(`Unsupported file type: ${file.mimetype}. Allowed: ${allowedMimes.join(', ')}`));
        }
    },
});

const router: Router = Router();

/**
 * POST /api/upload-prescription
 * 
 * Accepts a multipart form-data request with:
 * - file: The prescription image/PDF
 * - bucketId: The DataHaven bucket ID
 * 
 * Returns: { success, fileKey, bucketId, txHash, fingerprint, fileSize, uploadStatus }
 */
router.post(
    '/upload-prescription',
    upload.single('file'),
    async (req: Request, res: Response): Promise<void> => {
        const startTime = Date.now();

        try {
            // Validate file was uploaded
            if (!req.file) {
                res.status(400).json({
                    success: false,
                    error: 'No file uploaded. Use field name "file" in multipart form-data.',
                });
                return;
            }

            // Get bucket ID: from form body, or from server state (via res.locals)
            const bucketId = req.body.bucketId || res.locals.serverBucketId;
            if (!bucketId) {
                // Clean up uploaded file
                fs.unlinkSync(req.file.path);
                res.status(400).json({
                    success: false,
                    error: 'Missing bucketId in request body.',
                });
                return;
            }

            console.log(`\n📤 Upload request received:`);
            console.log(`   File: ${req.file.originalname} (${req.file.size} bytes)`);
            console.log(`   Bucket: ${bucketId}`);
            console.log(`   Saved to: ${req.file.path}`);

            // Upload to DataHaven
            const result = await uploadFile(
                bucketId,
                req.file.path,
                req.file.originalname,
            );

            // Wait for MSP to confirm on-chain
            console.log('[Upload] Waiting for MSP on-chain confirmation...');
            await waitForMSPConfirmOnChain(result.fileKey);

            // Wait for backend to index the file
            console.log('[Upload] Waiting for MSP backend to index file...');
            await waitForBackendFileReady(bucketId, result.fileKey);

            const duration = ((Date.now() - startTime) / 1000).toFixed(1);
            console.log(`\n✅ Upload complete in ${duration}s\n`);

            // Clean up local temp file after successful upload
            try {
                fs.unlinkSync(req.file.path);
            } catch (_) {
                // Non-critical if cleanup fails
            }

            res.json({
                success: true,
                fileKey: result.fileKey,
                bucketId,
                txHash: result.txHash,
                fingerprint: result.fingerprint,
                fileSize: result.fileSize,
                uploadStatus: result.uploadReceipt.status,
                durationSeconds: parseFloat(duration),
            });
        } catch (error: any) {
            console.error('[Upload] Error:', error.message);

            // Clean up uploaded file on error
            if (req.file) {
                try {
                    fs.unlinkSync(req.file.path);
                } catch (_) { }
            }

            res.status(500).json({
                success: false,
                error: error.message,
                durationSeconds: parseFloat(((Date.now() - startTime) / 1000).toFixed(1)),
            });
        }
    },
);

export default router;
