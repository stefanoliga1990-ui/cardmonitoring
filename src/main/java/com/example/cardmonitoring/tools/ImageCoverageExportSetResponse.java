package com.example.cardmonitoring.tools;

import java.util.List;

public record ImageCoverageExportSetResponse(
		ImageCoverageExportSetIdentity set,
		List<ImageCoverageExportCardResponse> cards) {
}
