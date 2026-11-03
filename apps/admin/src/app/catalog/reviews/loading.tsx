"use client";

import { LoadingSpinner } from "@/components/common/LoadingSpinner";

export default function ReviewsLoading() {
  return (
    <div className="flex h-[400px] items-center justify-center">
      <LoadingSpinner size={32} />
    </div>
  );
}
