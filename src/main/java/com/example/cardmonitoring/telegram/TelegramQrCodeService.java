package com.example.cardmonitoring.telegram;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

@Service
class TelegramQrCodeService {

	String createSvg(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("QR code value is required");
		}
		try {
			Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
			hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
			hints.put(EncodeHintType.MARGIN, 1);
			BitMatrix matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 1, 1, hints);
			return toSvg(matrix);
		}
		catch (WriterException exception) {
			throw new TelegramException("Unable to generate Telegram QR code", exception);
		}
	}

	private static String toSvg(BitMatrix matrix) {
		int width = matrix.getWidth();
		int height = matrix.getHeight();
		StringBuilder path = new StringBuilder(width * height / 2);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if (matrix.get(x, y)) {
					path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
				}
			}
		}
		return """
				<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" shape-rendering="crispEdges">
				<rect width="100%%" height="100%%" fill="#ffffff"/>
				<path fill="#17231f" d="%s"/>
				</svg>
				""".formatted(width, height, path);
	}
}
