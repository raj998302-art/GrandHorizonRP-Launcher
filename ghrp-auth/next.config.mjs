/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // The launcher WebView loads this page with query params (sysinfo, client_id, ...)
  // Never cache the HTML so auth state is always fresh.
  async headers() {
    return [
      {
        source: "/",
        headers: [
          { key: "Cache-Control", value: "no-store, max-age=0" },
        ],
      },
    ];
  },
};

export default nextConfig;
